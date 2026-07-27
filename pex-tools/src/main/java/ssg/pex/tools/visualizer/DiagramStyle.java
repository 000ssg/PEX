package ssg.pex.tools.visualizer;

import java.awt.Color;
import java.awt.Font;

/**
 * Configurable styling for railroad diagram rendering.
 *
 * @param terminalFillColor    fill color for terminal (literal) rounded rectangles
 * @param nonTerminalFillColor fill color for non-terminal rectangles
 * @param regexFillColor       fill color for regex terminal rectangles (dashed border)
 * @param textFont             font used for all text labels
 * @param textColor            color for text labels
 * @param lineColor            color for connecting lines and borders
 * @param arrowColor           color for arrow heads
 * @param backgroundColor      background color for the diagram
 * @param padding              internal padding inside boxes around text
 * @param arrowSize            size of arrow heads in pixels
 * @param gapH                 horizontal gap between elements
 * @param gapV                 vertical gap between alternatives
 * @param cornerRadius         corner radius for rounded rectangles
 */
public record DiagramStyle(
        Color terminalFillColor,
        Color nonTerminalFillColor,
        Color regexFillColor,
        Font textFont,
        Color textColor,
        Color lineColor,
        Color arrowColor,
        Color backgroundColor,
        int padding,
        int arrowSize,
        int gapH,
        int gapV,
        int cornerRadius
) {

    /**
     * Returns a default style with pleasant railroad diagram colors.
     */
    public static DiagramStyle defaultStyle() {
        return new DiagramStyle(
                new Color(255, 219, 179),   // warm peach for terminals
                new Color(179, 219, 255),   // light blue for non-terminals
                new Color(230, 230, 250),   // lavender for regex
                new Font("SansSerif", Font.PLAIN, 14),
                Color.BLACK,
                new Color(60, 60, 60),
                new Color(60, 60, 60),
                Color.WHITE,
                8,   // padding
                6,   // arrowSize
                20,  // gapH
                12,  // gapV
                10   // cornerRadius
        );
    }
}
