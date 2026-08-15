package com.aibuilder.lovableclone.generation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aibuilder.lovableclone.generation.dto.GeneratedAppDto;
import com.aibuilder.lovableclone.generation.dto.GeneratedFileDto;

// Validator ko model ki zarurat nahi, isliye yeh test bina quota kharch kiye
// reject-path saabit karta hai — jo end-to-end call kabhi reliably nahi kar sakti
class GeneratedAppValidatorTest {

    private final GeneratedAppValidator validator = new GeneratedAppValidator();

    private static final String VALID_HTML = """
            <!DOCTYPE html>
            <html><head>
              <script src="https://cdn.tailwindcss.com"></script>
              <script src="https://unpkg.com/react@18/umd/react.development.js"></script>
              <script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js"></script>
              <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
            </head><body>
              <div id="root"></div>
              <script type="text/babel" src="app.jsx"></script>
            </body></html>
            """;

    private static final String VALID_JSX = """
            function App() {
              return <p>hi</p>;
            }
            ReactDOM.createRoot(document.getElementById('root')).render(<App />);
            """;

    private GeneratedAppDto app(String html, String jsx, String css) {
        return new GeneratedAppDto("Test", "A test app", List.of(
                new GeneratedFileDto("index.html", html),
                new GeneratedFileDto("app.jsx", jsx),
                new GeneratedFileDto("styles.css", css)));
    }

    @Test
    void acceptsAWellFormedApp() {
        assertThat(validator.validate(app(VALID_HTML, VALID_JSX, ""))).isEmpty();
    }

    // Yeh wahi defect hai jo asli generation mein aaya tha
    @Test
    void rejectsWrongCasedReactDom() {
        String jsx = VALID_JSX.replace("ReactDOM.createRoot", "ReactDom.createRoot");

        assertThat(validator.validate(app(VALID_HTML, jsx, "")))
                .anyMatch(violation -> violation.contains("ReactDom"));
    }

    @Test
    void rejectsTailwindLoadedAsAStylesheet() {
        String html = VALID_HTML.replace(
                "<script src=\"https://cdn.tailwindcss.com\"></script>",
                "<link rel=\"stylesheet\" href=\"https://cdn.tailwindcss.com\">");

        assertThat(validator.validate(app(html, VALID_JSX, "")))
                .anyMatch(violation -> violation.contains("link tag"));
    }

    @Test
    void rejectsTheRenderApiReact18Removed() {
        String jsx = VALID_JSX + "\nReactDOM.render(<App />, document.getElementById('root'));";

        assertThat(validator.validate(app(VALID_HTML, jsx, "")))
                .anyMatch(violation -> violation.contains("ReactDOM.render"));
    }

    @Test
    void rejectsImportStatementsBecauseThereIsNoBuildStep() {
        String jsx = "import React from 'react';\n" + VALID_JSX;

        assertThat(validator.validate(app(VALID_HTML, jsx, "")))
                .anyMatch(violation -> violation.contains("import statement"));
    }

    @Test
    void rejectsAMissingFile() {
        GeneratedAppDto incomplete = new GeneratedAppDto("Test", "A test app", List.of(
                new GeneratedFileDto("index.html", VALID_HTML),
                new GeneratedFileDto("app.jsx", VALID_JSX)));

        assertThat(validator.validate(incomplete))
                .anyMatch(violation -> violation.contains("styles.css is missing"));
    }

    // Duplicate path insert pe (project_id, path) unique constraint todta, 500 ban ke
    @Test
    void rejectsDuplicatePaths() {
        GeneratedAppDto duplicated = new GeneratedAppDto("Test", "A test app", List.of(
                new GeneratedFileDto("index.html", VALID_HTML),
                new GeneratedFileDto("app.jsx", VALID_JSX),
                new GeneratedFileDto("app.jsx", VALID_JSX),
                new GeneratedFileDto("styles.css", "")));

        assertThat(validator.validate(duplicated))
                .anyMatch(violation -> violation.contains("Duplicate file: app.jsx"));
    }

    @Test
    void allowsAnEmptyStylesheetButNotAnEmptyScript() {
        assertThat(validator.validate(app(VALID_HTML, VALID_JSX, ""))).isEmpty();

        assertThat(validator.validate(app(VALID_HTML, "", "")))
                .anyMatch(violation -> violation.contains("app.jsx is empty"));
    }

    @Test
    void rejectsMarkdownFences() {
        String fenced = "```jsx\n" + VALID_JSX + "\n```";

        assertThat(validator.validate(app(VALID_HTML, fenced, "")))
                .anyMatch(violation -> violation.contains("markdown fence"));
    }
}
