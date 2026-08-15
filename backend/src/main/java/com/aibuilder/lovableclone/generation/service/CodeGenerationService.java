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
import com.aibuilder.lovableclone.common.exception.ModelRateLimitedException;
import com.openai.errors.RateLimitException;
import com.aibuilder.lovableclone.generation.dto.ChatMessageResponseDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileResponseDto;
import com.aibuilder.lovableclone.generation.entity.MessageRoleEnum;
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
            - styles.css, as <link rel="stylesheet" href="styles.css">
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
            - The page runs with no network access and no secrets. Never call fetch or
              XMLHttpRequest, and never reference an API key. Requests are blocked by the
              sandbox, so an app that depends on one shows the user nothing at all
            - When the request implies live data, such as weather or prices, put realistic
              sample data in a constant in app.jsx and read from that. Say it is sample data
              somewhere visible, so the user is not misled

            styles.css holds only rules that Tailwind cannot express, and may be nearly empty.

            Format every file the way a person would write it: real line breaks between
            statements, JSX elements and CSS rules, and two-space indentation. Never put a
            whole file on one line. The user reads this code.

            Build exactly the features the request asks for. Do not invent extra controls.

            When the request includes the app as it currently stands, it is a change to that
            app, not a fresh start. Apply only what is asked and keep every other feature,
            wording and style exactly as it is. Always return all three files in full.

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
    private final ChatMessageService chatMessageService;
    private final GeneratedAppValidator validator;

    public CodeGenerationService(ChatClient chatClient,
                                 ProjectService projectService,
                                 GeneratedFileService generatedFileService,
                                 ChatMessageService chatMessageService,
                                 GeneratedAppValidator validator) {
        this.chatClient = chatClient;
        this.projectService = projectService;
        this.generatedFileService = generatedFileService;
        this.chatMessageService = chatMessageService;
        this.validator = validator;
    }

    public GeneratedAppDto generateForProject(Long projectId, Long ownerId, String prompt) {
        // Ownership check aur interlock ek hi atomic statement mein. Apne transaction mein
        // commit hota hai, taaki LLM fail hone pe bhi GENERATING zinda rahe. Double-click
        // ki doosri request yahin 409 le kar wapas jaati hai, model call se pehle
        projectService.claimForGeneration(projectId, ownerId);

        // Context model call se pehle padha jata hai, taaki naya prompt usme na aa jaye
        String context = buildContext(projectId, ownerId);

        // Prompt pehle likha jata hai, kyunki fail hui koshish bhi history hai — aur wahi
        // batati hai ki user ne kya maanga tha jab jawab nahi aaya
        chatMessageService.record(projectId, MessageRoleEnum.USER, prompt);

        try {
            GeneratedAppDto app = generateValidApp(projectId, prompt, context);
            generatedFileService.replaceFiles(projectId, app);
            // Poora code store ho chuka hai; message mein summary hi jaati hai, warna
            // history hi context window kha jayegi
            chatMessageService.record(projectId, MessageRoleEnum.ASSISTANT, app.summary());
            projectService.updateStatus(projectId, ownerId, ProjectStatusEnum.READY);
            return app;

        } catch (ModelRateLimitedException ex) {
            // Rate limit mein generation fail nahi hui — woh shuru hi nahi hui. Purani files
            // jaisi thi waisi hain aur preview abhi bhi chalti hai, isliye FAILED dikhana jhoot
            // hota. Jahan se claim kiya tha wahin wapas chhod dete hain
            releaseClaim(projectId, ownerId, ex);
            throw ex;

        } catch (RuntimeException ex) {
            markFailed(projectId, ownerId, ex);
            throw ex;
        }
    }

    /**
     * Pehli generation ke liye khaali, uske baad follow-up ka context.
     *
     * Follow-up ke do hisse hain, aur dono chahiye. Maujooda files batati hain "abhi kya hai" —
     * "header ko neela karo" bina current code ke poori app dobara likhwa deta, aur baaki sab
     * badal jata. History batati hai woh intent jo code mein dikhta nahi: "minimal rakho"
     * jaisi baat file padh kar pata nahi chalti.
     */
    private String buildContext(Long projectId, Long ownerId) {
        List<GeneratedFileResponseDto> files = generatedFileService.getFiles(projectId, ownerId);
        if (files.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        List<ChatMessageResponseDto> history = chatMessageService.getRecentForContext(projectId);
        if (!history.isEmpty()) {
            context.append("Earlier turns for this app, oldest first:\n");
            for (ChatMessageResponseDto message : history) {
                context.append(message.role() == MessageRoleEnum.USER ? "Asked: " : "Built: ")
                        .append(message.content())
                        .append('\n');
            }
            context.append('\n');
        }

        context.append("This is the app as it stands. Apply the change to it and return all"
                + " three files complete, keeping everything the request does not mention:\n\n");

        for (GeneratedFileResponseDto file : files) {
            context.append("--- ").append(file.path()).append(" ---\n")
                    .append(file.content()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * Claim wapas chhodta hai bina project ko FAILED kiye.
     *
     * Purana status yaad rakhne ke bajaye files se nikala jata hai, kyunki status batata hi
     * yeh hai ki abhi kya maujood hai: files hain to app chal rahi hai (READY), nahi hain to
     * project waisa hi khali hai jaisa bana tha (DRAFT). Isse ek purana FAILED bhi saaf ho
     * jata hai — theek hai, kyunki ab na kuch toota hua hai na kuch chal raha hai.
     */
    private void releaseClaim(Long projectId, Long ownerId, RuntimeException cause) {
        try {
            ProjectStatusEnum released = generatedFileService.getFiles(projectId, ownerId).isEmpty()
                    ? ProjectStatusEnum.DRAFT
                    : ProjectStatusEnum.READY;

            projectService.updateStatus(projectId, ownerId, released);

        } catch (RuntimeException whileReleasing) {
            // markFailed jaisa hi: cleanup ki failure asli wajah ko nigalni nahi chahiye
            cause.addSuppressed(whileReleasing);
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
    private GeneratedAppDto generateValidApp(Long projectId, String prompt, String context) {
        List<String> violations = List.of();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            GeneratedAppDto app = callModel(projectId, prompt, context, violations, attempt);
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

    private String userMessage(String prompt, String context, List<String> violations) {
        StringBuilder message = new StringBuilder();

        // Context pehle, request baad mein: instruction aakhir mein rehne se model use
        // background ke bajaye kaam samajhta hai
        if (!context.isEmpty()) {
            message.append(context);
        }
        message.append(prompt);

        if (!violations.isEmpty()) {
            // Model ko wahi wajahein wapas di jaati hain jinpe use reject kiya gaya
            message.append("\n\nYour previous attempt was rejected. Fix every point below and")
                    .append(" return the complete app again:\n")
                    .append(violations.stream().map(v -> "- " + v).collect(Collectors.joining("\n")));
        }

        return message.toString();
    }

    /**
     * Provider ki rate limit ko ek aisi cheez banata hai jo client ko batayi ja sakti hai.
     *
     * Bina iske yeh SDK exception catch-all tak pahunchti hai aur 500 "Something went wrong"
     * ban jaati hai — jo jhoot hai: request theek thi aur baad mein chal jayegi. Free tier pe
     * yeh aam haal hai, aur UI ko farq batana hi hai.
     *
     * SDK ka apna retry pehle ho chuka hota hai (spring.ai.openai.max-retries), isliye yahan
     * dobara koshish nahi hoti. Groq per-minute aur per-day dono limit deta hai, aur "kitni der"
     * ka jawab sirf retry-after mein hota hai — provider ka prose message parse karna bhangur hai
     */
    private ModelRateLimitedException asRateLimited(RateLimitException ex) {
        // Provider ka message org id jaisi cheezein rakhta hai, isliye woh log mein jata hai,
        // response mein nahi
        log.warn("Model provider rate limited the request: {}", ex.getMessage());

        return ex.headers().values("retry-after").stream()
                .findFirst()
                .map(this::describeWait)
                .map(wait -> new ModelRateLimitedException(
                        "The AI provider's rate limit was reached. Try again in about " + wait + ".", ex))
                .orElseGet(() -> new ModelRateLimitedException(
                        "The AI provider's rate limit was reached. Try again in a few minutes.", ex));
    }

    private String describeWait(String retryAfterSeconds) {
        try {
            long seconds = (long) Math.ceil(Double.parseDouble(retryAfterSeconds));
            return seconds < 90 ? seconds + " seconds" : Math.round(seconds / 60.0) + " minutes";
        } catch (NumberFormatException notASimpleDelay) {
            // retry-after ek HTTP date bhi ho sakta hai. Us haal mein waqt bataye bina hi kaam chalega
            return "a few minutes";
        }
    }

    private GeneratedAppDto callModel(Long projectId, String prompt, String context,
                                      List<String> violations, int attempt) {
        log.info("Generating app for project {} (attempt {} of {})", projectId, attempt, MAX_ATTEMPTS);
        long startedAt = System.currentTimeMillis();

        GeneratedAppDto app;
        try {
            app = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage(prompt, context, violations))
                    // JSON mode: provider decoding ko constrain karta hai, isliye escaping
                    // ki galti namumkin ho jati hai. maxTokens truncation ke against insurance
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                                    .build())
                            .maxTokens(8000))
                    .call()
                    .entity(GeneratedAppDto.class);

        } catch (RateLimitException ex) {
            throw asRateLimited(ex);
        }

        log.info("Model returned {} files for project {} in {} ms",
                app.files().size(), projectId, System.currentTimeMillis() - startedAt);

        return app;
    }
}
