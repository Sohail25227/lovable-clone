package com.aibuilder.lovableclone.generation.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import com.aibuilder.lovableclone.common.exception.GenerationFailedException;
import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.validation.GeneratedAppValidator;
import com.aibuilder.lovableclone.workspace.entity.ProjectStatusEnum;
import com.aibuilder.lovableclone.workspace.service.ProjectService;

@Service
public class CodeGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert frontend engineer. You generate complete, working
            single-page web applications from a short description.
            Your entire reply is a single JSON object and nothing else.

            Produce exactly these three files: index.html, app.jsx and styles.css

            index.html must load, in this order:
            - Tailwind as a script: <script src="https://cdn.tailwindcss.com"></script>
              It is not a stylesheet, so never load it with a link tag
            - React 18 and ReactDOM 18 UMD builds from unpkg
            - Babel standalone from unpkg
            - a div whose id is root
            - a script tag with type text/babel and src app.jsx

            app.jsx rules:
            - Define a function component named App
            - Mount it with ReactDOM.createRoot(document.getElementById('root')).render(<App />)
            - The global is spelled ReactDOM, with DOM in capital letters.
              ReactDom is undefined and crashes the page
            - Never use ReactDOM.render, it was removed in React 18
            - Use onKeyDown, never the removed onKeyPress
            - Read saved state from localStorage once, during useState initialisation
            - Write to localStorage in exactly one useEffect that depends on that state.
              Never call localStorage.setItem inside an event handler
            - Ignore any submission that is empty after trimming
            - When the collection is empty, render a short friendly message instead of the list
            - Give every item in a list its own li element
            - Use only browser-native APIs. No import statements, no npm packages, no build step

            styles.css holds only rules that Tailwind cannot express, and may be nearly empty.

            Build exactly the features the request asks for. Do not invent extra controls.

            Visual bar: generous whitespace, one accent colour used consistently,
            visible hover and focus styles on everything clickable, and a layout that
            stays usable on a narrow phone screen.

            Never explain your work and never use markdown fences.
            """;

    // Ek retry. Do se zyada karne se latency aur free-tier quota dono jaldi khatam hote hain
    private static final int MAX_ATTEMPTS = 2;

    private final ChatClient chatClient;
    private final ProjectService projectService;
    private final GeneratedFileService generatedFileService;
    private final GeneratedAppValidator validator;

    public CodeGenerationService(ChatClient chatClient,
                                 ProjectService projectService,
                                 GeneratedFileService generatedFileService,
                                 GeneratedAppValidator validator) {
        this.chatClient = chatClient;
        this.projectService = projectService;
        this.generatedFileService = generatedFileService;
        this.validator = validator;
    }

    public GeneratedAppDto generateForProject(Long projectId, Long ownerId, String prompt) {
        // Ownership check. Project nahi mila ya tumhara nahi to yahi 404 fenk dega
        projectService.getProjectById(projectId, ownerId);

        // Apne transaction mein commit hota hai, taaki LLM fail hone pe bhi zinda rahe
        projectService.updateStatus(projectId, ownerId, ProjectStatusEnum.GENERATING);

        try {
            GeneratedAppDto app = generateValidApp(projectId, prompt);
            generatedFileService.replaceFiles(projectId, app);
            projectService.updateStatus(projectId, ownerId, ProjectStatusEnum.READY);
            return app;

        } catch (RuntimeException ex) {
            markFailed(projectId, ownerId, ex);
            throw ex;
        }
    }

    private void markFailed(Long projectId, Long ownerId, RuntimeException cause) {
        try {
            projectService.updateStatus(projectId, ownerId, ProjectStatusEnum.FAILED);
        } catch (RuntimeException whileMarking) {
            // Cleanup ki apni failure asli wajah ko nigalni nahi chahiye
            cause.addSuppressed(whileMarking);
        }
    }

    // Prompt ke rules request hain, guarantee nahi — model unhe todta rehta hai. Isliye
    // output check hota hai aur violations wapas bhej ke ek sudharne ka mauka milta hai.
    // Invalid code kabhi store nahi hota, warna READY ka matlab jhooth ho jata
    private GeneratedAppDto generateValidApp(Long projectId, String prompt) {
        List<String> violations = List.of();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            GeneratedAppDto app = callModel(projectId, prompt, violations, attempt);
            violations = validator.validate(app);

            if (violations.isEmpty()) {
                return app;
            }
            log.warn("Attempt {} of {} for project {} rejected: {}",
                    attempt, MAX_ATTEMPTS, projectId, violations);
        }

        throw new GenerationFailedException(
                "The model could not produce a runnable app: " + String.join("; ", violations));
    }

    private String userMessage(String prompt, List<String> violations) {
        if (violations.isEmpty()) {
            return prompt;
        }
        // Model ko wahi wajahein wapas di jaati hain jinpe use reject kiya gaya
        return prompt
                + "\n\nYour previous attempt was rejected. Fix every point below and return"
                + " the complete app again:\n"
                + violations.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
    }

    private GeneratedAppDto callModel(Long projectId, String prompt,
                                      List<String> violations, int attempt) {
        log.info("Generating app for project {} (attempt {} of {})", projectId, attempt, MAX_ATTEMPTS);
        long startedAt = System.currentTimeMillis();

        GeneratedAppDto app = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage(prompt, violations))
                // JSON mode: provider decoding ko constrain karta hai, isliye escaping
                // ki galti namumkin ho jati hai. maxTokens truncation ke against insurance
                .options(OpenAiChatOptions.builder()
                        .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .maxTokens(8000))
                .call()
                .entity(GeneratedAppDto.class);

        log.info("Model returned {} files for project {} in {} ms",
                app.files().size(), projectId, System.currentTimeMillis() - startedAt);

        return app;
    }
}
