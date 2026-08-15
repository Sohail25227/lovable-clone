package com.aibuilder.lovableclone.generation.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileDto;

// Sirf wo rules yahan hain jo app ko chalne se rokte hain. Taste prompt mein rehta hai,
// warna "kam whitespace" jaisi cheez pe retry jalega. Rejection ka matlab hamesha yeh
// hona chahiye ki app browser mein wakai toot jaati
@Component
public class GeneratedAppValidator {

    private static final String INDEX_HTML = "index.html";
    private static final String APP_JSX = "app.jsx";
    private static final String STYLES_CSS = "styles.css";

    private static final Pattern ROOT_ELEMENT = Pattern.compile("id\\s*=\\s*[\"']root[\"']");
    private static final Pattern BABEL_SCRIPT = Pattern.compile("type\\s*=\\s*[\"']text/babel[\"']");
    private static final Pattern TAILWIND_AS_LINK = Pattern.compile("<link[^>]*cdn\\.tailwindcss\\.com");
    private static final Pattern APP_COMPONENT = Pattern.compile("function\\s+App\\b|(?:const|let|var)\\s+App\\s*=");
    private static final Pattern ES_IMPORT = Pattern.compile("(?m)^\\s*import\\s");

    // Khaali list ka matlab output chalne layak hai
    public List<String> validate(GeneratedAppDto app) {
        List<String> violations = new ArrayList<>();

        if (app.files() == null || app.files().isEmpty()) {
            violations.add("No files were returned");
            return violations;
        }

        Map<String, String> byPath = collectFiles(app.files(), violations);

        for (String required : List.of(INDEX_HTML, APP_JSX, STYLES_CSS)) {
            if (!byPath.containsKey(required)) {
                violations.add(required + " is missing");
            }
        }

        byPath.forEach((path, content) -> {
            // Fences JSON mode se bach jaate hain, kyunki wo string ke andar valid hain
            if (content.contains("```")) {
                violations.add(path + " contains a markdown fence");
            }
            // styles.css ko khaali rehne ki ijazat hai, baaki ko nahi
            if (content.isBlank() && !STYLES_CSS.equals(path)) {
                violations.add(path + " is empty");
            }
        });

        if (byPath.containsKey(INDEX_HTML)) {
            validateIndexHtml(byPath.get(INDEX_HTML), violations);
        }
        if (byPath.containsKey(APP_JSX)) {
            validateAppJsx(byPath.get(APP_JSX), violations);
        }

        return violations;
    }

    private Map<String, String> collectFiles(List<GeneratedFileDto> files, List<String> violations) {
        Map<String, String> byPath = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (GeneratedFileDto file : files) {
            if (file.path() == null || file.path().isBlank()) {
                violations.add("A file was returned with no path");
                continue;
            }
            // Duplicate path insert pe (project_id, path) unique constraint todta hai
            if (!seen.add(file.path())) {
                violations.add("Duplicate file: " + file.path());
                continue;
            }
            if (file.content() == null) {
                violations.add(file.path() + " has no content");
                continue;
            }
            byPath.put(file.path(), file.content());
        }

        return byPath;
    }

    private void validateIndexHtml(String html, List<String> violations) {
        if (!html.contains("cdn.tailwindcss.com")) {
            violations.add("index.html does not load Tailwind from https://cdn.tailwindcss.com");
        }
        if (TAILWIND_AS_LINK.matcher(html).find()) {
            violations.add("index.html loads Tailwind with a link tag; the CDN build is a script, "
                    + "so a stylesheet link leaves the page unstyled");
        }
        if (!html.contains("/react@18")) {
            violations.add("index.html does not load the React 18 UMD build from unpkg");
        }
        if (!html.contains("/react-dom@18")) {
            violations.add("index.html does not load the ReactDOM 18 UMD build from unpkg");
        }
        if (!html.contains("babel")) {
            violations.add("index.html does not load Babel standalone, so JSX will never compile");
        }
        if (!ROOT_ELEMENT.matcher(html).find()) {
            violations.add("index.html has no element with id root to mount into");
        }
        if (!BABEL_SCRIPT.matcher(html).find()) {
            violations.add("index.html has no script tag with type text/babel");
        }
        if (!html.contains(APP_JSX)) {
            violations.add("index.html never references app.jsx");
        }
    }

    private void validateAppJsx(String jsx, List<String> violations) {
        if (!jsx.contains("ReactDOM.createRoot")) {
            violations.add("app.jsx does not mount with ReactDOM.createRoot");
        }
        if (jsx.contains("ReactDOM.render")) {
            violations.add("app.jsx uses ReactDOM.render, which React 18 removed");
        }
        if (jsx.contains("ReactDom")) {
            violations.add("app.jsx spells the global ReactDom; it is ReactDOM, with DOM in capitals, "
                    + "and the wrong spelling is undefined at runtime");
        }
        if (ES_IMPORT.matcher(jsx).find()) {
            violations.add("app.jsx uses an import statement, which cannot resolve without a build step");
        }
        if (jsx.contains("require(")) {
            violations.add("app.jsx uses require(), which the browser does not provide");
        }
        if (jsx.contains("onKeyPress")) {
            violations.add("app.jsx uses the removed onKeyPress; use onKeyDown");
        }
        if (!APP_COMPONENT.matcher(jsx).find()) {
            violations.add("app.jsx does not define a component named App");
        }
    }
}
