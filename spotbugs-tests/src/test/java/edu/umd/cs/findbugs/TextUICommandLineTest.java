package edu.umd.cs.findbugs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUICommandLineTest {
    @Test
    void handleOutputFileReturnsRemainingPart() throws IOException {
        Path file = Files.createTempFile("spotbugs", ".xml");
        TextUICommandLine commandLine = new TextUICommandLine();
        SortingBugReporter reporter = new SortingBugReporter();
        String result = commandLine.handleOutputFilePath(reporter, "withMessages=" + file.toFile().getAbsolutePath());

        assertThat(result, is("withMessages"));
    }

    @Test
    void handleOutputFilePathUsesGzip() throws IOException {
        Path file = Files.createTempFile("spotbugs", ".xml.gz");
        TextUICommandLine commandLine = new TextUICommandLine();
        SortingBugReporter reporter = new SortingBugReporter();
        commandLine.handleOutputFilePath(reporter, "withMessages=" + file.toFile().getAbsolutePath());

        reporter.finish();
        byte[] written = Files.readAllBytes(file);
        assertThat("GZip file should have 31 as its header", written[0], is((byte) 31));
        assertThat("GZip file should have -117 as its header", written[1], is((byte) -117));
    }

    @Test
    void handleOutputFileTruncatesExisting() throws IOException {
        Path file = Files.createTempFile("spotbugs", ".html");
        Files.writeString(file, "content");
        TextUICommandLine commandLine = new TextUICommandLine();
        SortingBugReporter reporter = new SortingBugReporter();
        commandLine.handleOutputFilePath(reporter, "withMessages=" + file.toFile().getAbsolutePath());

        reporter.finish();
        byte[] written = Files.readAllBytes(file);
        assertThat("Output file should be truncated to 0 bytes", written.length, is(0));
    }

    @Test
    void htmlReportWithOption() throws IOException {
        Path xmlFile = Files.createTempFile("spotbugs", ".xml");
        Path htmlFile = Files.createTempFile("spotbugs", ".html");
        Path sarifFile = Files.createTempFile("spotbugs", ".sarif");
        Path emacsFile = Files.createTempFile("spotbugs", ".emacs");
        Path xdocsFile = Files.createTempFile("spotbugs", ".xdocs");
        Path textFile = Files.createTempFile("spotbugs", ".txt");

        TextUICommandLine commandLine = new TextUICommandLine();
        try (FindBugs2 findbugs = new FindBugs2()) {
            commandLine.handleOption("-xml", "=" + xmlFile.toFile().getAbsolutePath());
            commandLine.handleOption("-html", "fancy-hist.xsl=" + htmlFile.toFile().getAbsolutePath());
            commandLine.handleOption("-sarif", "=" + sarifFile.toFile().getAbsolutePath());
            commandLine.handleOption("-emacs", "=" + emacsFile.toFile().getAbsolutePath());
            commandLine.handleOption("-xdocs", "=" + xdocsFile.toFile().getAbsolutePath());
            commandLine.handleOption("-sortByClass", "=" + textFile.toFile().getAbsolutePath());
            commandLine.configureEngine(findbugs);
            findbugs.getBugReporter().finish();
        }
        String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
        assertThat(html, containsString("#historyTab"));

        assertTrue(xmlFile.toFile().isFile());
        assertTrue(sarifFile.toFile().isFile());
        assertTrue(emacsFile.toFile().isFile());
        assertTrue(xdocsFile.toFile().isFile());
        assertTrue(textFile.toFile().isFile());
    }

    @Test
    void sharedOutputFilesAreNotDuplicated() throws Exception {
        Path file = Files.createTempFile("spotbugs", ".html");
        TextUICommandLine commandLine = new TextUICommandLine();
        try (FindBugs2 findbugs = new FindBugs2()) {
            commandLine.handleOption("-html", "fancy-hist.xsl=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-xml", "withMessages=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-xml", "=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-sarif", "=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-emacs", "=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-xdocs", "=" + file.toFile().getAbsolutePath());
            commandLine.handleOption("-sortByClass", "=" + file.toFile().getAbsolutePath());

            commandLine.configureEngine(findbugs);
            var configuredReporter = findbugs.getBugReporter();
            // this is a horrible hack: This would be a BugReportDispatcher if there was more than one reporter
            assertThat(configuredReporter, not(isA(BugReportDispatcher.class)));
            configuredReporter.finish();
        }
        // we don't even *really* need to finish analysis, but it's better to explicitly check
        String html = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(html, containsString("#historyTab"));
    }

    @Test
    void deduplicationUsesCanonicalPaths() throws Exception {
        Path tmpdir = Files.createTempDirectory("spotbugs");
        Files.createDirectory(tmpdir.resolve("sibling1"));
        Files.createDirectory(tmpdir.resolve("sibling2"));
        Path file = Files.createTempFile(tmpdir.resolve("sibling1"), "tempFile", ".xml");
        Path altFile = tmpdir.resolve("sibling2").resolve("..").resolve(file.getFileName());
        TextUICommandLine commandLine = new TextUICommandLine();
        try (FindBugs2 findbugs = new FindBugs2()) {
            commandLine.handleOption("-xml", "=" + file);
            commandLine.handleOption("-xml", "=" + altFile);

            commandLine.configureEngine(findbugs);
            var configuredReporter = findbugs.getBugReporter();
            // this is a horrible hack: This would be a BugReportDispatcher if there was more than one reporter
            assertThat(configuredReporter, not(isA(BugReportDispatcher.class)));
            configuredReporter.finish();
        }
        // we don't even *really* need to finish analysis, but it's better to explicitly check
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(xml, containsString("BugCollection"));
    }
}
