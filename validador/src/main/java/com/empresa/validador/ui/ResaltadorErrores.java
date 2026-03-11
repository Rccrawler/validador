package com.empresa.validador.ui;

import org.languagetool.rules.RuleMatch;

import javax.swing.text.*;
import java.awt.*;
import java.util.List;

/**
 * Aplica subrayado de color en el JTextPane según los errores detectados.
 * Rojo    → errores ortográficos/gramaticales
 * Naranja → sugerencias de estilo
 * Azul    → hints / mejoras
 */
public class ResaltadorErrores {

    // Atributos de resaltado por tipo
    private static final SimpleAttributeSet ESTILO_ERROR;
    private static final SimpleAttributeSet ESTILO_SUGERENCIA;
    private static final SimpleAttributeSet ESTILO_NORMAL;

    static {
        ESTILO_ERROR = new SimpleAttributeSet();
        StyleConstants.setForeground(ESTILO_ERROR, new Color(180, 0, 0));
        StyleConstants.setBold(ESTILO_ERROR, true);
        StyleConstants.setUnderline(ESTILO_ERROR, true);

        ESTILO_SUGERENCIA = new SimpleAttributeSet();
        StyleConstants.setForeground(ESTILO_SUGERENCIA, new Color(180, 100, 0));
        StyleConstants.setUnderline(ESTILO_SUGERENCIA, true);

        ESTILO_NORMAL = new SimpleAttributeSet();
        StyleConstants.setForeground(ESTILO_NORMAL, Color.BLACK);
        StyleConstants.setBold(ESTILO_NORMAL, false);
        StyleConstants.setUnderline(ESTILO_NORMAL, false);
    }

    /**
     * Quita todo el resaltado previo y aplica el nuevo según la lista de errores.
     */
    public static void aplicar(StyledDocument doc, List<RuleMatch> errores) {
        // Limpia estilos anteriores
        doc.setCharacterAttributes(0, doc.getLength(), ESTILO_NORMAL, true);

        for (RuleMatch match : errores) {
            SimpleAttributeSet estilo =
                match.getType() == RuleMatch.Type.Hint ? ESTILO_SUGERENCIA : ESTILO_ERROR;
            doc.setCharacterAttributes(
                match.getFromPos(),
                match.getToPos() - match.getFromPos(),
                estilo,
                false
            );
        }
    }
}