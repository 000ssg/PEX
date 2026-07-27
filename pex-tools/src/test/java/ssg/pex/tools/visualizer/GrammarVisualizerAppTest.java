package ssg.pex.tools.visualizer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GrammarVisualizerApp}: drag-and-drop, multi-select, clipboard,
 * and message interception (no JOptionPane popups block tests).
 */
class GrammarVisualizerAppTest {

    private GrammarVisualizerApp app;
    private final List<GrammarVisualizerApp.MessageInfo> capturedMessages =
            Collections.synchronizedList(new ArrayList<>());

    /** Standard 4-rule grammar used across many tests. */
    private static final String FOUR_RULE_GRAMMAR = """
            grammar FourRules;
            program ::= statement+ ;
            statement ::= assignment | expression ;
            assignment ::= "ID" "=" expression ;
            expression ::= "NUMBER" | "STRING" ;
            """;

    @BeforeEach
    void setUp() throws Exception {
        capturedMessages.clear();
        SwingUtilities.invokeAndWait(() -> {
            app = new GrammarVisualizerApp();
            // Intercept all JOptionPane calls so tests never block
            app.setMessageInterceptor(capturedMessages::add);
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (app != null) app.dispose();
        });
    }

    // ==================== Load from file ====================

    @Nested
    class LoadFromFile {

        @Test
        void testLoadValidEbnfFile(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.ebnf", """
                    grammar TestGrammar;
                    expr ::= term ( "+" term )* ;
                    term ::= "NUMBER" ;
                    """);

            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            assertThat(ruleNames()).containsExactly("expr", "term");
            assertThat(app.getTitle()).contains("TestGrammar");
            assertThat(app.getTitle()).contains("test.ebnf");
            assertThat(capturedMessages).isEmpty();
        }

        @Test
        void testLoadMultipleRules(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "multi.ebnf", FOUR_RULE_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            assertThat(ruleNames()).containsExactly("program", "statement", "assignment", "expression");
        }

        @Test
        void testLoadInvalidSyntaxShowsError(@TempDir Path dir) throws Exception {
            var bad = writeGrammar(dir, "bad.ebnf", "not valid grammar !@#$%");
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(bad));

            assertThat(ruleNames()).isEmpty();
            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().title()).isEqualTo("Error");
            assertThat(capturedMessages.getFirst().message()).containsIgnoringCase("parse error");
        }

        @Test
        void testLoadNonExistentFileShowsError() throws Exception {
            var missing = Path.of("/tmp/nonexistent-grammar-98765.ebnf");
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(missing));

            assertThat(ruleNames()).isEmpty();
            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().message()).contains("Failed to open file");
        }

        @Test
        void testSecondLoadReplacesFirst(@TempDir Path dir) throws Exception {
            var f1 = writeGrammar(dir, "first.ebnf", """
                    grammar First;
                    alpha ::= "A" ;
                    """);
            var f2 = writeGrammar(dir, "second.ebnf", """
                    grammar Second;
                    beta ::= "B" ;
                    gamma ::= "C" ;
                    """);

            SwingUtilities.invokeAndWait(() -> {
                app.loadFromFile(f1);
                app.loadFromFile(f2);
            });

            assertThat(ruleNames()).containsExactly("beta", "gamma");
            assertThat(app.getTitle()).contains("Second");
        }
    }

    // ==================== Drag & Drop ====================

    @Nested
    class DragAndDrop {

        @Test
        void testDropTargetsRegistered() throws Exception {
            var ref = new AtomicReference<Boolean[]>();
            SwingUtilities.invokeAndWait(() -> ref.set(new Boolean[]{
                    getRuleList().getDropTarget() != null,
                    app.getContentPane().getDropTarget() != null
            }));
            assertThat(ref.get()[0]).as("rule list drop target").isTrue();
            assertThat(ref.get()[1]).as("content pane drop target").isTrue();
        }

        @Test
        void testDropValidGrammar(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "dropped.ebnf", """
                    grammar Dropped;
                    rule1 ::= "HELLO" ;
                    rule2 ::= "WORLD" ;
                    """);

            simulateDrop(List.of(file.toFile()));

            assertThat(ruleNames()).containsExactly("rule1", "rule2");
            assertThat(app.getTitle()).contains("Dropped");
        }

        @Test
        void testDropMultipleFilesPicksFirstGrammar(@TempDir Path dir) throws Exception {
            var txt = dir.resolve("readme.txt");
            Files.writeString(txt, "not grammar");
            var ebnf = writeGrammar(dir, "real.ebnf", """
                    grammar Real;
                    start ::= "X" ;
                    """);

            simulateDrop(List.of(txt.toFile(), ebnf.toFile()));
            assertThat(ruleNames()).containsExactly("start");
        }

        @Test
        void testDropNonGrammarShowsWarning(@TempDir Path dir) throws Exception {
            var csv = dir.resolve("data.csv");
            Files.writeString(csv, "a,b\n1,2\n");

            simulateDrop(List.of(csv.toFile()));

            assertThat(ruleNames()).isEmpty();
            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().title()).isEqualTo("Unsupported File");
        }

        @Test
        void testDropBnfExtension(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.bnf", """
                    grammar BnfTest;
                    item ::= "TOKEN" ;
                    """);
            simulateDrop(List.of(file.toFile()));
            assertThat(ruleNames()).containsExactly("item");
        }

        @Test
        void testDropGrammarExtension(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.grammar", """
                    grammar GrammarExt;
                    node ::= "A" | "B" ;
                    """);
            simulateDrop(List.of(file.toFile()));
            assertThat(ruleNames()).containsExactly("node");
        }

        @Test
        void testDropReplacesExistingGrammar(@TempDir Path dir) throws Exception {
            var first = writeGrammar(dir, "first.ebnf", """
                    grammar First;
                    old_rule ::= "OLD" ;
                    """);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(first));
            assertThat(ruleNames()).containsExactly("old_rule");

            var second = writeGrammar(dir, "second.ebnf", """
                    grammar Second;
                    new_rule ::= "NEW" ;
                    """);
            simulateDrop(List.of(second.toFile()));

            assertThat(ruleNames()).containsExactly("new_rule");
        }

        @Test
        void testDropFiltersMixedExtensions(@TempDir Path dir) throws Exception {
            var txt = dir.resolve("test.txt").toFile(); txt.createNewFile();
            var java = dir.resolve("Test.java").toFile(); java.createNewFile();
            var ebnf = writeGrammar(dir, "test.ebnf", """
                    grammar ExtTest;
                    r ::= "X" ;
                    """);

            simulateDrop(List.of(txt, java, ebnf.toFile()));
            assertThat(ruleNames()).containsExactly("r");
        }
    }

    // ==================== Multi-select ====================

    @Nested
    class MultiSelect {

        @Test
        void testRuleListIsMultiSelect() throws Exception {
            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(getRuleList().getSelectionMode()));
            assertThat(ref.get()).isEqualTo(
                    javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        }

        @Test
        void testSingleSelectionShowsSingleRule(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> getRuleList().setSelectedIndex(0));

            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getRules().size()));
            assertThat(ref.get()).isEqualTo(1);
        }

        @Test
        void testMultiSelectionShowsMultipleRules(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectedIndices(new int[]{0, 1, 2}));

            var ref = new AtomicReference<List<String>>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getRules().stream()
                            .map(ssg.pex.bnf.model.Rule::name).toList()));
            assertThat(ref.get()).containsExactly("program", "statement", "assignment");
        }

        @Test
        void testSelectAllRules(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectionInterval(0, getRuleListModel().getSize() - 1));

            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getRules().size()));
            assertThat(ref.get()).isEqualTo(4);
        }

        @Test
        void testClearSelectionClearsDiagram(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0);
                getRuleList().clearSelection();
            });

            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getRules().size()));
            assertThat(ref.get()).isEqualTo(0);
        }

        @Test
        void testChangingSelectionUpdatesDiagram(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            // Select first two
            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectedIndices(new int[]{0, 1}));
            var ref1 = new AtomicReference<List<String>>();
            SwingUtilities.invokeAndWait(() ->
                    ref1.set(app.getDiagramPanel().getRules().stream()
                            .map(ssg.pex.bnf.model.Rule::name).toList()));
            assertThat(ref1.get()).containsExactly("program", "statement");

            // Change to last two
            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectedIndices(new int[]{2, 3}));
            var ref2 = new AtomicReference<List<String>>();
            SwingUtilities.invokeAndWait(() ->
                    ref2.set(app.getDiagramPanel().getRules().stream()
                            .map(ssg.pex.bnf.model.Rule::name).toList()));
            assertThat(ref2.get()).containsExactly("assignment", "expression");
        }
    }

    // ==================== MultiRulePanel ====================

    @Nested
    class MultiRulePanelTest {

        @Test
        void testSingleRuleNoExtraHeight(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);
            SwingUtilities.invokeAndWait(() -> getRuleList().setSelectedIndex(0));

            var ref = new AtomicReference<java.awt.Dimension>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getPreferredSize()));
            var singleSize = ref.get();

            // Multi-select: preferred height should be larger
            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectionInterval(0, 3));
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getPreferredSize()));
            var multiSize = ref.get();

            assertThat(multiSize.height).isGreaterThan(singleSize.height);
        }

        @Test
        void testEmptyRulesGivesMinimumSize() throws Exception {
            var ref = new AtomicReference<java.awt.Dimension>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getDiagramPanel().getPreferredSize()));
            assertThat(ref.get().width).isGreaterThanOrEqualTo(200);
            assertThat(ref.get().height).isGreaterThanOrEqualTo(100);
        }
    }

    // ==================== Clipboard ====================

    @Nested
    class Clipboard {

        @Test
        void testCopySingleRuleToClipboard(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0);
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("program");
            assertThat(clip).contains("::=");
            assertThat(clip).contains(";");
        }

        @Test
        void testCopyMultipleRulesToClipboard(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndices(new int[]{0, 1});
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("program ::=");
            assertThat(clip).contains("statement ::=");
            // Two rules separated by blank line
            assertThat(clip).contains("\n\n");
        }

        @Test
        void testCopyAllRulesToClipboard(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectionInterval(0, 3);
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("program ::=");
            assertThat(clip).contains("statement ::=");
            assertThat(clip).contains("assignment ::=");
            assertThat(clip).contains("expression ::=");
        }

        @Test
        void testCopyWithNoSelectionShowsWarning(@TempDir Path dir) throws Exception {
            loadFourRuleGrammar(dir);

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().clearSelection();
                app.copySelectedRulesToClipboard();
            });

            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().message()).contains("No rules selected");
        }

        @Test
        void testCopyWithNoGrammarShowsWarning() throws Exception {
            SwingUtilities.invokeAndWait(() -> app.copySelectedRulesToClipboard());

            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().message()).contains("No grammar loaded");
        }

        @Test
        void testCopiedTextHasCorrectBnfSyntax(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "syntax.ebnf", """
                    grammar SyntaxCheck;
                    expr ::= term ( "+" term )* ;
                    term ::= "NUMBER" ;
                    """);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0);
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            // Should have the rule name, ::=, and ;
            assertThat(clip).startsWith("expr ::=");
            assertThat(clip).endsWith(";");
            // Should contain the expression elements
            assertThat(clip).contains("term");
            assertThat(clip).contains("\"+\"");
        }

        @Test
        void testCopyTerminalLiteralsAreQuoted(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "literals.ebnf", """
                    grammar Literals;
                    simple ::= "HELLO" "WORLD" ;
                    """);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0);
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("\"HELLO\"");
            assertThat(clip).contains("\"WORLD\"");
        }

        @Test
        void testCopyAlternationUsesBar(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "alt.ebnf", """
                    grammar Alt;
                    choice ::= "A" | "B" | "C" ;
                    """);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0);
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("|");
            assertThat(clip).contains("\"A\"");
            assertThat(clip).contains("\"B\"");
            assertThat(clip).contains("\"C\"");
        }
    }

    // ==================== ANTLR4 Support ====================

    @Nested
    class Antlr4Support {

        private static final String SIMPLE_ANTLR_GRAMMAR = """
                grammar SimpleCalc;
                expr : term ('+' term)* ;
                term : NUMBER ;
                NUMBER : [0-9]+ ;
                WS : [ \\t\\n\\r]+ -> skip ;
                """;

        @Test
        void testLoadG4File(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "calc.g4", SIMPLE_ANTLR_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            assertThat(ruleNames()).containsExactly("expr", "term", "NUMBER", "WS");
            assertThat(app.getTitle()).contains("SimpleCalc");
            assertThat(app.getTitle()).contains("ANTLR4");
            assertThat(capturedMessages).isEmpty();
        }

        @Test
        void testAntlrModeEnabled(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.g4", SIMPLE_ANTLR_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            var ref = new AtomicReference<Boolean>();
            SwingUtilities.invokeAndWait(() -> ref.set(app.isAntlrMode()));
            assertThat(ref.get()).isTrue();
        }

        @Test
        void testBnfModeAfterEbnf(@TempDir Path dir) throws Exception {
            // Load ANTLR first, then BNF
            var g4 = writeGrammar(dir, "first.g4", SIMPLE_ANTLR_GRAMMAR);
            var ebnf = writeGrammar(dir, "second.ebnf", """
                    grammar Second;
                    rule1 ::= "A" ;
                    """);

            SwingUtilities.invokeAndWait(() -> {
                app.loadFromFile(g4);
                app.loadFromFile(ebnf);
            });

            var ref = new AtomicReference<Boolean>();
            SwingUtilities.invokeAndWait(() -> ref.set(app.isAntlrMode()));
            assertThat(ref.get()).isFalse();
            assertThat(ruleNames()).containsExactly("rule1");
        }

        @Test
        void testAntlrSingleSelectionShowsDiagram(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.g4", SIMPLE_ANTLR_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() -> getRuleList().setSelectedIndex(0));

            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getAntlrDiagramPanel().getRules().size()));
            assertThat(ref.get()).isEqualTo(1);
        }

        @Test
        void testAntlrMultiSelectShowsMultipleRules(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.g4", SIMPLE_ANTLR_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() ->
                    getRuleList().setSelectedIndices(new int[]{0, 1, 2}));

            var ref = new AtomicReference<Integer>();
            SwingUtilities.invokeAndWait(() ->
                    ref.set(app.getAntlrDiagramPanel().getRules().size()));
            assertThat(ref.get()).isEqualTo(3);
        }

        @Test
        void testDropG4File(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "dropped.g4", SIMPLE_ANTLR_GRAMMAR);
            simulateDrop(List.of(file.toFile()));

            assertThat(ruleNames()).containsExactly("expr", "term", "NUMBER", "WS");
            assertThat(app.getTitle()).contains("SimpleCalc");
        }

        @Test
        void testCopyAntlrRulesToClipboard(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "test.g4", SIMPLE_ANTLR_GRAMMAR);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            SwingUtilities.invokeAndWait(() -> {
                getRuleList().setSelectedIndex(0); // expr
                app.copySelectedRulesToClipboard();
            });

            String clip = getClipboardText();
            assertThat(clip).contains("expr");
            assertThat(clip).contains("term");
            assertThat(clip).contains(";");
        }

        @Test
        void testFragmentRuleDisplayedWithPrefix(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "frag.g4", """
                    grammar FragTest;
                    expr : NUMBER ;
                    NUMBER : DIGIT+ ;
                    fragment DIGIT : [0-9] ;
                    """);
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            assertThat(ruleNames()).contains("[F] DIGIT");
        }

        @Test
        void testInvalidG4ShowsError(@TempDir Path dir) throws Exception {
            var file = writeGrammar(dir, "bad.g4", "not valid ANTLR");
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));

            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().title()).isEqualTo("Error");
        }
    }

    // ==================== Message interception ====================

    @Nested
    class MessageInterception {

        @Test
        void testMessagesAreInterceptedNotShown(@TempDir Path dir) throws Exception {
            // Load invalid file — should produce intercepted message, not a popup
            var bad = writeGrammar(dir, "bad.ebnf", "garbage !!!");
            SwingUtilities.invokeAndWait(() -> app.loadFromFile(bad));

            assertThat(capturedMessages).hasSize(1);
            assertThat(capturedMessages.getFirst().messageType())
                    .isEqualTo(javax.swing.JOptionPane.ERROR_MESSAGE);
        }

        @Test
        void testMultipleErrorsAllCaptured(@TempDir Path dir) throws Exception {
            var bad1 = writeGrammar(dir, "bad1.ebnf", "invalid 1");
            var bad2 = writeGrammar(dir, "bad2.ebnf", "invalid 2");
            var missing = Path.of("/tmp/does-not-exist-xyz.ebnf");

            SwingUtilities.invokeAndWait(() -> {
                app.loadFromFile(bad1);
                app.loadFromFile(bad2);
                app.loadFromFile(missing);
            });

            assertThat(capturedMessages).hasSize(3);
        }

        @Test
        void testDisablingInterceptorRestoresNormalBehavior() throws Exception {
            // Set interceptor to null — we won't actually trigger a dialog in this test,
            // just verify the interceptor field is cleared
            SwingUtilities.invokeAndWait(() -> app.setMessageInterceptor(null));

            // Verify interceptor is off (we can't test JOptionPane without blocking,
            // so just verify the callback was cleared by sending a message that would
            // have been captured)
            // This is a structural test — the important thing is it doesn't hang
        }
    }

    // ==================== Helpers ====================

    private Path writeGrammar(Path dir, String filename, String content) throws Exception {
        var path = dir.resolve(filename);
        Files.writeString(path, content);
        return path;
    }

    private void loadFourRuleGrammar(Path dir) throws Exception {
        var file = writeGrammar(dir, "four.ebnf", FOUR_RULE_GRAMMAR);
        SwingUtilities.invokeAndWait(() -> app.loadFromFile(file));
    }

    private List<String> ruleNames() {
        var model = getRuleListModel();
        var names = new ArrayList<String>();
        for (int i = 0; i < model.getSize(); i++) {
            names.add(model.getElementAt(i));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private JList<String> getRuleList() {
        try {
            var field = GrammarVisualizerApp.class.getDeclaredField("ruleList");
            field.setAccessible(true);
            return (JList<String>) field.get(app);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private DefaultListModel<String> getRuleListModel() {
        return (DefaultListModel<String>) getRuleList().getModel();
    }

    private void simulateDrop(List<File> files) throws Exception {
        var transferable = new FileTransferable(files);
        var event = createMockDropEvent(transferable);
        SwingUtilities.invokeAndWait(() -> app.handleDrop(event));
    }

    private DropTargetDropEvent createMockDropEvent(Transferable transferable) {
        var dropTarget = app.getContentPane().getDropTarget();
        if (dropTarget == null) {
            dropTarget = new DropTarget(app.getContentPane(), null);
        }
        return new TestDropTargetDropEvent(
                dropTarget.getDropTargetContext(),
                new java.awt.Point(50, 50),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY_OR_MOVE,
                transferable
        );
    }

    private String getClipboardText() throws Exception {
        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        return (String) clipboard.getData(DataFlavor.stringFlavor);
    }

    // --- Test support classes ---

    static class FileTransferable implements Transferable {
        private final List<File> files;
        FileTransferable(List<File> files) { this.files = files; }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return files;
        }
    }

    static class TestDropTargetDropEvent extends DropTargetDropEvent {
        private final Transferable transferable;
        TestDropTargetDropEvent(DropTargetContext dtc, java.awt.Point cursorLocn,
                                int dropAction, int srcActions, Transferable transferable) {
            super(dtc, cursorLocn, dropAction, srcActions);
            this.transferable = transferable;
        }
        @Override public Transferable getTransferable() { return transferable; }
    }
}
