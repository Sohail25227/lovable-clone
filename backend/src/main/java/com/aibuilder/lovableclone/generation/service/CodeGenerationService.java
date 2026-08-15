package com.aibuilder.lovableclone.generation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
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
            - Tailwind from https://cdn.tailwindcss.com
            - React 18 and ReactDOM 18 UMD builds from unpkg
            - Babel standalone from unpkg
            - a div whose id is root
            - a script tag with type text/babel and src app.jsx

            app.jsx rules:
            - Define a function component named App
            - Mount it with ReactDOM.createRoot(document.getElementById('root')).render(<App />)
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

            Visual bar: generous whitespace, one accent colour used consistently,
            visible hover and focus styles on everything clickable, and a layout that
            stays usable on a narrow phone screen.

            Never explain your work and never use markdown fences.
            """;

    private final ChatClient chatClient;
    private final ProjectService projectService;
    private final GeneratedFileService generatedFileService;

    public CodeGenerationService(ChatClient chatClient,
                                 ProjectService projectService,
                                 GeneratedFileService generatedFileService) {
        this.chatClient = chatClient;
        this.projectService = projectService;
        this.generatedFileService = generatedFileService;
    }

    public GeneratedAppDto generateForProject(Long projectId, Long ownerId, String prompt) {
        // Ownership check. Project nahi mila ya tumhara nahi to yahi 404 fenk dega
        projectService.getProjectById(projectId, ownerId);

        // Apne transaction mein commit hota hai, taaki LLM fail hone pe bhi zinda rahe
        projectService.updateStatus(projectId, ownerId, ProjectStatusEnum.GENERATING);

        try {
            GeneratedAppDto app = callModel(projectId, prompt);
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

    private GeneratedAppDto callModel(Long projectId, String prompt) {
        log.info("Generating app for project {}", projectId);
        long startedAt = System.currentTimeMillis();

        GeneratedAppDto app = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                // JSON mode: provider decoding ko constrain karta hai, isliye escaping
                // ki galti namumkin ho jati hai. maxTokens truncation ke against insurance
                .options(OpenAiChatOptions.builder()
                        .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .maxTokens(8000))
                .call()
                .entity(GeneratedAppDto.class);

        log.info("Generated {} files for project {} in {} ms",
                app.files().size(), projectId, System.currentTimeMillis() - startedAt);

        return app;
    }
}
