package ssg.pex.tools.converter;

import ssg.pex.bnf.model.Grammar;

import java.util.List;

/**
 * Result of a grammar conversion operation.
 *
 * <p>Holds either a text output (BNF to ANTLR) or a Grammar output (ANTLR to BNF),
 * along with any warnings generated during conversion.
 *
 * @param textOutput    the ANTLR grammar text (for BNF-to-ANTLR conversions), or {@code null}
 * @param grammarOutput the PEX Grammar (for ANTLR-to-BNF conversions), or {@code null}
 * @param warnings      non-fatal issues encountered during conversion
 */
public record ConversionResult(String textOutput, Grammar grammarOutput, List<String> warnings) {

    public ConversionResult {
        warnings = List.copyOf(warnings);
    }

    /**
     * Creates a result containing ANTLR text output.
     */
    public static ConversionResult ofText(String text, List<String> warnings) {
        return new ConversionResult(text, null, warnings);
    }

    /**
     * Creates a result containing a PEX Grammar.
     */
    public static ConversionResult ofGrammar(Grammar grammar, List<String> warnings) {
        return new ConversionResult(null, grammar, warnings);
    }
}
