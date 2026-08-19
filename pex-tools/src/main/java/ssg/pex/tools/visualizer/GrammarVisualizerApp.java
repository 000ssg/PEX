package ssg.pex.tools.visualizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.pex.bnf.model.Alternation;
import ssg.pex.bnf.model.Grammar;
import ssg.pex.bnf.model.Group;
import ssg.pex.bnf.model.NonTerminal;
import ssg.pex.bnf.model.Repetition;
import ssg.pex.bnf.model.Rule;
import ssg.pex.bnf.model.RuleExpression;
import ssg.pex.bnf.model.Sequence;
import ssg.pex.bnf.model.Terminal;
import ssg.pex.bnf.parser.BnfParser;
import ssg.pex.tools.converter.antlr.AntlrExpression;
import ssg.pex.tools.converter.antlr.AntlrGrammar;
import ssg.pex.tools.converter.antlr.AntlrGrammarParser;
import ssg.pex.tools.converter.antlr.AntlrRule;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Main Swing application for visualizing BNF/EBNF and ANTLR4 grammars as railroad diagrams.
 *
 * <p>Features:
 * <ul>
 *   <li>Left panel: list of rule names from the loaded grammar (multi-select enabled)</li>
 *   <li>Right panel: railroad diagrams for the selected rule(s), each with titled border when
 *       more than one rule is selected</li>
 *   <li>Menu: open grammar files, export to SVG/PNG, copy selected rules to clipboard</li>
 *   <li>Drag-and-drop: drop .ebnf/.bnf/.grammar/.g4 files onto the rule list,
 *       diagram panel, or any part of the window to load them</li>
 *   <li>Clipboard: Ctrl+C / Cmd+C copies the BNF text of selected rules</li>
 *   <li>ANTLR support: .g4 files are parsed natively, preserving all ANTLR constructs
 *       including predicates, actions, negation, and lexer commands</li>
 * </ul>
 */
public final class GrammarVisualizerApp extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(GrammarVisualizerApp.class);
    private static final Set<String> ACCEPTED_EXTENSIONS = Set.of("ebnf", "bnf", "grammar", "g4");

    private Grammar grammar;
    private AntlrGrammar antlrGrammar;
    private boolean isAntlrMode;
    private DiagramStyle style = DiagramStyle.defaultStyle();
    private final DefaultListModel<String> ruleListModel = new DefaultListModel<>();
    private final JList<String> ruleList = new JList<>(ruleListModel);
    private MultiRulePanel diagramPanel;
    private AntlrMultiRulePanel antlrDiagramPanel;
    private JScrollPane diagramScroll;
    private final BnfParser parser = new BnfParser();
    private final AntlrGrammarParser antlrParser = new AntlrGrammarParser();
    private final SvgExporter svgExporter = new SvgExporter();
    private final PngExporter pngExporter = new PngExporter();

    // --- Testability: message interception ---
    // When non-null, messages are routed here instead of JOptionPane
    private Consumer<MessageInfo> messageInterceptor;

    /** Captures info about a message that would have been shown via JOptionPane. */
    public record MessageInfo(String message, String title, int messageType) {}

    /**
     * Install an interceptor that receives messages instead of showing JOptionPane dialogs.
     * Useful for testing. Pass {@code null} to restore normal JOptionPane behavior.
     */
    void setMessageInterceptor(Consumer<MessageInfo> interceptor) {
        this.messageInterceptor = interceptor;
    }

    public GrammarVisualizerApp() {
        super("PEX Grammar Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1024, 768));
        initComponents();
        initMenu();
        initDragAndDrop();
        pack();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        // Left panel: rule list with multi-select
        ruleList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        ruleList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        ruleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onRulesSelected();
            }
        });
        var listScroll = new JScrollPane(ruleList);
        listScroll.setPreferredSize(new Dimension(200, 600));
        listScroll.setBorder(BorderFactory.createTitledBorder("Rules (multi-select: Ctrl/Shift+click)"));

        // Right panel: multi-rule diagram (BNF and ANTLR panels)
        diagramPanel = new MultiRulePanel(style);
        antlrDiagramPanel = new AntlrMultiRulePanel(style);
        diagramScroll = new JScrollPane(diagramPanel);
        diagramScroll.setBorder(BorderFactory.createTitledBorder("Railroad Diagram"));

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, diagramScroll);
        splitPane.setDividerLocation(200);
        add(splitPane, BorderLayout.CENTER);
    }

    private void initMenu() {
        var menuBar = new JMenuBar();

        var fileMenu = new JMenu("File");

        var openItem = new JMenuItem("Open Grammar File...");
        openItem.addActionListener(e -> onOpenGrammar());
        fileMenu.add(openItem);

        fileMenu.addSeparator();

        var exportSvgItem = new JMenuItem("Export SVG...");
        exportSvgItem.addActionListener(e -> onExportSvg());
        fileMenu.add(exportSvgItem);

        var exportPngItem = new JMenuItem("Export PNG...");
        exportPngItem.addActionListener(e -> onExportPng());
        fileMenu.add(exportPngItem);

        fileMenu.addSeparator();

        var exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // Edit menu with Copy
        var editMenu = new JMenu("Edit");

        var copyItem = new JMenuItem("Copy Rules to Clipboard");
        int modifierMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, modifierMask));
        copyItem.addActionListener(e -> copySelectedRulesToClipboard());
        editMenu.add(copyItem);

        var selectAllItem = new JMenuItem("Select All Rules");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, modifierMask));
        selectAllItem.addActionListener(e -> {
            if (ruleListModel.getSize() > 0) {
                ruleList.setSelectionInterval(0, ruleListModel.getSize() - 1);
            }
        });
        editMenu.add(selectAllItem);

        menuBar.add(editMenu);
        setJMenuBar(menuBar);
    }

    private void initDragAndDrop() {
        var dropHandler = new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                handleDrop(event);
            }
        };
        // Accept drops on the rule list, the diagram panel, and the frame's content pane
        new DropTarget(ruleList, DnDConstants.ACTION_COPY_OR_MOVE, dropHandler, true);
        new DropTarget(diagramPanel, DnDConstants.ACTION_COPY_OR_MOVE, dropHandler, true);
        new DropTarget(getContentPane(), DnDConstants.ACTION_COPY_OR_MOVE, dropHandler, true);
        log.debug("Drag-and-drop initialized on rule list, diagram panel, and content pane");
    }

    @SuppressWarnings("unchecked")
    void handleDrop(DropTargetDropEvent event) {
        try {
            if (!event.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.rejectDrop();
                return;
            }
            event.acceptDrop(DnDConstants.ACTION_COPY);
            var files = (List<File>) event.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
            event.dropComplete(true);
            if (files.isEmpty()) return;

            // Take the first grammar file from the dropped list
            var grammarFile = files.stream()
                    .filter(this::isGrammarFile)
                    .findFirst()
                    .orElse(null);

            if (grammarFile == null) {
                showMessage(
                        "No grammar file found in dropped items.\n"
                                + "Accepted extensions: .ebnf, .bnf, .grammar, .g4",
                        "Unsupported File", JOptionPane.WARNING_MESSAGE);
                return;
            }

            loadFromFile(grammarFile.toPath());

        } catch (UnsupportedFlavorException | IOException ex) {
            log.error("Drag-and-drop failed", ex);
            showMessage("Drag-and-drop failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isGrammarFile(File file) {
        if (!file.isFile()) return false;
        var name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return ACCEPTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    /**
     * Load a grammar from the given file path. Parses the file and updates
     * the rule list and diagram. This method is used by both the menu "Open"
     * action and drag-and-drop.
     *
     * @param path the path to the grammar file
     */
    void loadFromFile(Path path) {
        try {
            var source = Files.readString(path);
            String fileName = path.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".g4")) {
                // ANTLR4 grammar — use native ANTLR parser
                var result = antlrParser.parse(source);
                if (result.isSuccess()) {
                    loadAntlrGrammar(result.value());
                    setTitle("PEX Grammar Visualizer - " + antlrGrammar.name()
                            + "  [" + path.getFileName() + "]  (ANTLR4)");
                    log.info("Loaded ANTLR grammar from file: {}", path);
                } else {
                    showMessage("Parse error in " + path.getFileName() + ":\n"
                                    + result.error().message(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                // BNF/EBNF grammar — use BNF parser
                var result = parser.parse(source);
                if (result.isSuccess()) {
                    loadGrammar(result.value());
                    setTitle("PEX Grammar Visualizer - " + grammar.name()
                            + "  [" + path.getFileName() + "]");
                    log.info("Loaded grammar from file: {}", path);
                } else {
                    showMessage("Parse error in " + path.getFileName() + ":\n"
                                    + result.error().message(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to open grammar file: {}", path, ex);
            showMessage("Failed to open file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onOpenGrammar() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Grammar Files (BNF/EBNF/ANTLR)", "ebnf", "bnf", "grammar", "g4"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadFromFile(chooser.getSelectedFile().toPath());
        }
    }

    private void onExportSvg() {
        if (grammar == null) {
            showMessage("No grammar loaded.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("SVG Files", "svg"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            var path = chooser.getSelectedFile().toPath();
            if (!path.toString().endsWith(".svg")) {
                path = Path.of(path.toString() + ".svg");
            }
            var result = svgExporter.exportGrammar(grammar, style);
            if (result.isSuccess()) {
                try {
                    Files.writeString(path, result.value());
                    showMessage("SVG exported to: " + path,
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showMessage("Failed to write SVG: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                showMessage("Export failed: " + result.error().message(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onExportPng() {
        if (grammar == null) {
            showMessage("No grammar loaded.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            var dir = chooser.getSelectedFile().toPath();
            var result = pngExporter.exportGrammar(grammar, style, dir);
            if (result.isSuccess()) {
                showMessage("PNG files exported to: " + dir,
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } else {
                showMessage("Export failed: " + result.error().message(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Copy the BNF text of the currently selected rules to the system clipboard.
     * Each rule is formatted as {@code ruleName ::= expression ;} separated by blank lines.
     */
    void copySelectedRulesToClipboard() {
        if (grammar == null && antlrGrammar == null) {
            showMessage("No grammar loaded.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        var selectedNames = ruleList.getSelectedValuesList();
        if (selectedNames.isEmpty()) {
            showMessage("No rules selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        var sb = new StringBuilder();
        if (isAntlrMode) {
            for (var displayName : selectedNames) {
                var rule = findAntlrRuleByDisplayName(displayName);
                if (rule != null) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(formatAntlrRule(rule));
                }
            }
        } else {
            for (var name : selectedNames) {
                var rule = grammar.rules().get(name);
                if (rule != null) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(rule.name()).append(" ::= ").append(formatExpression(rule.body())).append(" ;");
                }
            }
        }

        var selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        log.info("Copied {} rule(s) to clipboard", selectedNames.size());
    }

    private String formatExpression(RuleExpression expr) {
        return switch (expr) {
            case Terminal t ->
                    t.isRegex() ? t.value() : "\"" + t.value() + "\"";
            case NonTerminal nt -> nt.ruleName();
            case Sequence seq ->
                    seq.elements().stream().map(this::formatExpression).reduce((a, b) -> a + " " + b).orElse("");
            case Alternation alt ->
                    alt.alternatives().stream().map(this::formatExpression).reduce((a, b) -> a + " | " + b).orElse("");
            case Repetition rep ->
                    formatExpression(rep.body()) + switch (rep.kind()) {
                        case ZERO_OR_MORE -> "*";
                        case ONE_OR_MORE -> "+";
                        case OPTIONAL -> "?";
                    };
            case Group grp -> "( " + formatExpression(grp.inner()) + " )";
        };
    }

    private void loadGrammar(Grammar grammar) {
        this.grammar = grammar;
        this.antlrGrammar = null;
        this.isAntlrMode = false;
        ruleListModel.clear();
        for (var name : grammar.rules().keySet()) {
            ruleListModel.addElement(name);
        }
        // Switch to BNF diagram panel
        diagramScroll.setViewportView(diagramPanel);
        if (!ruleListModel.isEmpty()) {
            ruleList.setSelectedIndex(0);
        }
        log.info("Loaded grammar '{}' with {} rules", grammar.name(), grammar.rules().size());
    }

    private void loadAntlrGrammar(AntlrGrammar antlrGrammar) {
        this.antlrGrammar = antlrGrammar;
        this.grammar = null;
        this.isAntlrMode = true;
        ruleListModel.clear();
        for (var rule : antlrGrammar.rules()) {
            String prefix = switch (rule.kind()) {
                case PARSER -> "";
                case LEXER -> "";
                case FRAGMENT -> "[F] ";
            };
            ruleListModel.addElement(prefix + rule.name());
        }
        // Also add mode rules
        for (var entry : antlrGrammar.modes().entrySet()) {
            for (var rule : entry.getValue()) {
                ruleListModel.addElement("[" + entry.getKey() + "] " + rule.name());
            }
        }
        // Switch to ANTLR diagram panel
        diagramScroll.setViewportView(antlrDiagramPanel);
        if (!ruleListModel.isEmpty()) {
            ruleList.setSelectedIndex(0);
        }
        log.info("Loaded ANTLR grammar '{}' ({}) with {} rules, {} modes",
                antlrGrammar.name(), antlrGrammar.grammarKind(),
                antlrGrammar.rules().size(), antlrGrammar.modes().size());
    }

    private void onRulesSelected() {
        if (isAntlrMode) {
            onAntlrRulesSelected();
        } else {
            onBnfRulesSelected();
        }
    }

    private void onBnfRulesSelected() {
        if (grammar == null) return;
        var selectedNames = ruleList.getSelectedValuesList();
        if (selectedNames.isEmpty()) {
            diagramPanel.setRules(List.of());
            return;
        }

        var selectedRules = new ArrayList<Rule>();
        for (var name : selectedNames) {
            var rule = grammar.rules().get(name);
            if (rule != null) selectedRules.add(rule);
        }

        if (selectedRules.size() == 1) {
            diagramPanel.setRule(selectedRules.getFirst());
        } else {
            diagramPanel.setRules(selectedRules);
        }
    }

    private void onAntlrRulesSelected() {
        if (antlrGrammar == null) return;
        var selectedNames = ruleList.getSelectedValuesList();
        if (selectedNames.isEmpty()) {
            antlrDiagramPanel.setRules(List.of());
            return;
        }

        var selectedRules = new ArrayList<AntlrRule>();
        for (var displayName : selectedNames) {
            var rule = findAntlrRuleByDisplayName(displayName);
            if (rule != null) selectedRules.add(rule);
        }

        if (selectedRules.size() == 1) {
            antlrDiagramPanel.setRule(selectedRules.getFirst());
        } else {
            antlrDiagramPanel.setRules(selectedRules);
        }
    }

    private AntlrRule findAntlrRuleByDisplayName(String displayName) {
        // Strip prefix markers like "[F] " or "[ModeName] "
        String cleanName = displayName;
        if (displayName.startsWith("[")) {
            int closeBracket = displayName.indexOf("] ");
            if (closeBracket >= 0) {
                String prefix = displayName.substring(1, closeBracket);
                cleanName = displayName.substring(closeBracket + 2);
                // Check if it's a mode rule
                if (!"F".equals(prefix)) {
                    var modeRules = antlrGrammar.modes().get(prefix);
                    if (modeRules != null) {
                        for (var rule : modeRules) {
                            if (rule.name().equals(cleanName)) return rule;
                        }
                    }
                }
            }
        }
        // Search in main rules
        for (var rule : antlrGrammar.rules()) {
            if (rule.name().equals(cleanName)) return rule;
        }
        return null;
    }

    /**
     * Returns the BNF diagram panel (package-visible for testing).
     */
    MultiRulePanel getDiagramPanel() {
        return diagramPanel;
    }

    /**
     * Returns the ANTLR diagram panel (package-visible for testing).
     */
    AntlrMultiRulePanel getAntlrDiagramPanel() {
        return antlrDiagramPanel;
    }

    /**
     * Returns whether the app is currently in ANTLR mode.
     */
    boolean isAntlrMode() {
        return isAntlrMode;
    }

    private String formatAntlrRule(AntlrRule rule) {
        var sb = new StringBuilder();
        if (rule.kind() == AntlrRule.RuleKind.FRAGMENT) sb.append("fragment ");
        sb.append(rule.name()).append("\n    : ");
        sb.append(formatAntlrExpression(rule.body()));
        if (!rule.commands().isEmpty()) {
            sb.append(" -> ").append(String.join(", ", rule.commands()));
        }
        sb.append("\n    ;");
        return sb.toString();
    }

    private String formatAntlrExpression(AntlrExpression expr) {
        return switch (expr) {
            case AntlrExpression.Literal lit -> "'" + lit.value() + "'";
            case AntlrExpression.CharClass cc -> cc.pattern();
            case AntlrExpression.RuleRef ref -> ref.name();
            case AntlrExpression.TokenRef ref -> ref.name();
            case AntlrExpression.Seq seq ->
                    seq.elements().stream().map(this::formatAntlrExpression).reduce((a, b) -> a + " " + b).orElse("");
            case AntlrExpression.Alt alt ->
                    alt.alternatives().stream().map(this::formatAntlrExpression).reduce((a, b) -> a + " | " + b).orElse("");
            case AntlrExpression.ZeroOrMore zom -> formatAntlrExpression(zom.body()) + "*";
            case AntlrExpression.OneOrMore oom -> formatAntlrExpression(oom.body()) + "+";
            case AntlrExpression.Optional opt -> formatAntlrExpression(opt.body()) + "?";
            case AntlrExpression.Group grp -> "(" + formatAntlrExpression(grp.inner()) + ")";
            case AntlrExpression.Negation neg -> "~" + formatAntlrExpression(neg.inner());
            case AntlrExpression.Predicate pred -> "{" + pred.code() + "}?";
            case AntlrExpression.Action act -> "{" + act.code() + "}";
            case AntlrExpression.Dot _ -> ".";
            case AntlrExpression.LabeledElement le ->
                    le.label() + (le.listLabel() ? "+=" : "=") + formatAntlrExpression(le.element());
        };
    }

    /**
     * Show a message — either via JOptionPane or the interceptor if set.
     */
    private void showMessage(String message, String title, int messageType) {
        if (messageInterceptor != null) {
            messageInterceptor.accept(new MessageInfo(message, title, messageType));
        } else {
            JOptionPane.showMessageDialog(this, message, title, messageType);
        }
    }

    /**
     * Launch the Grammar Visualizer application.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            var app = new GrammarVisualizerApp();
            app.setVisible(true);
        });
    }
}
