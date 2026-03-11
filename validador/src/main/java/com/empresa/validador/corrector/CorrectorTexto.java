package com.empresa.validador.corrector;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.List;

/**
 * Motor de corrección basado en LanguageTool.
 * Detecta: faltas ortográficas, errores gramaticales,
 * palabras mal escritas, orden incorrecto de palabras, etc.
 * Funciona 100% en local, sin internet.
 */
public class CorrectorTexto {

    private final JLanguageTool languageTool;

    public CorrectorTexto() throws IOException {
        // Carga español usando el API genérico (funciona en todas las versiones de LT)
        Language espanol = Languages.getLanguageForShortCode("es");
        this.languageTool = new JLanguageTool(espanol);
        // Deshabilita reglas de estilo tipográfico muy estrictas
        // que generan falsos positivos en entorno empresarial
        languageTool.disableRule("WHITESPACE_RULE");
        languageTool.disableRule("UNPAIRED_BRACKETS");
    }

    /**
     * Analiza el texto y devuelve la lista de errores encontrados.
     */
    public List<RuleMatch> analizar(String texto) throws IOException {
        return languageTool.check(texto);
    }

    /**
     * Devuelve una descripción legible del tipo de error.
     */
    public static String obtenerTipoError(RuleMatch match) {
        if (match.getType() == RuleMatch.Type.Hint) {
            return "💡 Sugerencia de estilo";
        } else if (match.getType() == RuleMatch.Type.Other) {
            return "⚠ Posible error";
        } else {
            return "❌ Error";
        }
    }
}
