package com.empresa.validador;

import com.empresa.validador.ui.VentanaPrincipal;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Asegura que la UI corre en el hilo de eventos de Swing
        SwingUtilities.invokeLater(() -> {
            try {
                // Look and Feel nativo del sistema operativo
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Si no hay L&F nativo, usa el por defecto, no es crítico
            }
            new VentanaPrincipal().setVisible(true);
        });
    }
}