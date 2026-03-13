package com.empresa.validador.ui;

import com.empresa.validador.corrector.CorrectorTexto;
import org.languagetool.rules.RuleMatch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Ventana principal de la aplicación.
 * Layout:
 *  ┌─────────────────────────────────────────────┐
 *  │  Barra de herramientas (Analizar | Limpiar) │
 *  ├─────────────────────────────────────────────┤
 *  │  Panel izquierdo: Texto editable            │
 *  │  Panel derecho:   Lista de errores          │
 *  ├─────────────────────────────────────────────┤
 *  │  Barra de estado                            │
 *  └─────────────────────────────────────────────┘
 */
public class VentanaPrincipal extends JFrame {

    // ── Componentes UI ──────────────────────────────────────────────────────
    private final JTextPane areaTexto;
    private final DefaultListModel<String> modeloErrores;
    private final JList<String> listaErrores;
    private final JLabel barraEstado;
    private final JButton btnAnalizar;
    private final JButton btnLimpiar;
    private final JButton btnCorregirTodo;

    // ── Motor de corrección ──────────────────────────────────────────────────
    private CorrectorTexto corrector;
    private List<RuleMatch> ultimosErrores;

    // ── Colores corporativos neutros ─────────────────────────────────────────
    private static final Color COLOR_FONDO      = new Color(245, 245, 248);
    private static final Color COLOR_BARRA      = new Color(30,  50,  80);
    private static final Color COLOR_BTN        = new Color(50,  90, 150);
    private static final Color COLOR_BTN_HOVER  = new Color(70, 120, 190);
    private static final Color COLOR_BTN_DANGER = new Color(180, 50,  40);

    public VentanaPrincipal() {
        super("Validador de Texto Empresarial — v1.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // ── Inicializar motor (en hilo aparte para no bloquear la UI) ────────
        barraEstado = new JLabel("  ⏳ Cargando motor de corrección en español…");
        barraEstado.setFont(new Font("SansSerif", Font.PLAIN, 12));
        barraEstado.setBorder(new EmptyBorder(4, 8, 4, 8));

        // ── Área de texto ────────────────────────────────────────────────────
        areaTexto = new JTextPane();
        areaTexto.setFont(new Font("Serif", Font.PLAIN, 15));
        areaTexto.setMargin(new Insets(10, 10, 10, 10));
        areaTexto.setEnabled(false); // se habilita cuando carga el motor

        // ── Lista de errores ─────────────────────────────────────────────────
        modeloErrores = new DefaultListModel<>();
        listaErrores  = new JList<>(modeloErrores);
        listaErrores.setFont(new Font("SansSerif", Font.PLAIN, 12));
        listaErrores.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaErrores.setCellRenderer(new ErrorCellRenderer());

        // Clic en un error → selecciona el texto correspondiente
        listaErrores.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && ultimosErrores != null) {
                int idx = listaErrores.getSelectedIndex();
                if (idx >= 0 && idx < ultimosErrores.size()) {
                    RuleMatch match = ultimosErrores.get(idx);
                    areaTexto.setSelectionStart(match.getFromPos());
                    areaTexto.setSelectionEnd(match.getToPos());
                    areaTexto.requestFocus();
                }
            }
        });

        // ── Botones ──────────────────────────────────────────────────────────
        btnAnalizar    = crearBoton("🔍 Analizar",      COLOR_BTN);
        btnLimpiar     = crearBoton("🗑 Limpiar",       COLOR_BTN);
        btnCorregirTodo = crearBoton("✅ Corregir todo", new Color(40, 130, 70));

        btnAnalizar.setEnabled(false);
        btnLimpiar.setEnabled(false);
        btnCorregirTodo.setEnabled(false);

        btnAnalizar.addActionListener(e -> analizarTexto());
        btnLimpiar.addActionListener(e -> limpiarTodo());
        btnCorregirTodo.addActionListener(e -> corregirTodo());

        // Atajo de teclado: F5 para analizar
        areaTexto.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "analizar");
        areaTexto.getActionMap().put("analizar", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { analizarTexto(); }
        });

        // ── Layout ───────────────────────────────────────────────────────────
        construirLayout();

        // ── Cargar motor en background ────────────────────────────────────
        cargarMotorEnBackground();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Construcción del layout
    // ─────────────────────────────────────────────────────────────────────────

    private void construirLayout() {
        getContentPane().setBackground(COLOR_FONDO);
        setLayout(new BorderLayout(0, 0));

        // Barra superior
        JPanel barraHerramientas = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        barraHerramientas.setBackground(COLOR_BARRA);
        barraHerramientas.add(crearLogoLabel());
        barraHerramientas.add(Box.createHorizontalStrut(10));
        barraHerramientas.add(btnAnalizar);
        barraHerramientas.add(btnCorregirTodo);
        barraHerramientas.add(btnLimpiar);

        // Panel central dividido
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(620);
        splitPane.setResizeWeight(0.65);

        // Izquierda: editor
        JScrollPane scrollTexto = new JScrollPane(areaTexto);
        scrollTexto.setBorder(BorderFactory.createTitledBorder("📝 Texto a validar   (F5 para analizar)"));
        splitPane.setLeftComponent(scrollTexto);

        // Derecha: panel de errores
        JPanel panelErrores = new JPanel(new BorderLayout(0, 4));
        panelErrores.setBorder(new EmptyBorder(4, 4, 4, 4));
        panelErrores.setBackground(COLOR_FONDO);

        JLabel lblTitulo = new JLabel("📋 Errores encontrados");
        lblTitulo.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblTitulo.setBorder(new EmptyBorder(4, 4, 4, 4));

        JScrollPane scrollErrores = new JScrollPane(listaErrores);
        scrollErrores.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        panelErrores.add(lblTitulo, BorderLayout.NORTH);
        panelErrores.add(scrollErrores, BorderLayout.CENTER);
        splitPane.setRightComponent(panelErrores);

        add(barraHerramientas, BorderLayout.NORTH);
        add(splitPane,         BorderLayout.CENTER);
        add(barraEstado,       BorderLayout.SOUTH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Carga del motor en background
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarMotorEnBackground() {
        SwingWorker<CorrectorTexto, Void> worker = new SwingWorker<>() {
            @Override
            protected CorrectorTexto doInBackground() throws Exception {
                return new CorrectorTexto();
            }

            @Override
            protected void done() {
                try {
                    corrector = get();
                    areaTexto.setEnabled(true);
                    btnAnalizar.setEnabled(true);
                    btnLimpiar.setEnabled(true);
                    barraEstado.setText("  ✅ Motor listo. Escribe o pega texto y pulsa Analizar (F5).");
                    areaTexto.requestFocus();
                } catch (Exception ex) {
                    barraEstado.setText("  ❌ Error al cargar el motor: " + ex.getMessage());
                    JOptionPane.showMessageDialog(VentanaPrincipal.this,
                        "No se pudo inicializar el corrector:\n" + ex.getMessage(),
                        "Error de inicialización", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lógica de análisis
    // ─────────────────────────────────────────────────────────────────────────

    private void analizarTexto() {
        String texto = areaTexto.getText().trim();
        if (texto.isEmpty()) {
            barraEstado.setText("  ⚠ El área de texto está vacía.");
            return;
        }

        btnAnalizar.setEnabled(false);
        barraEstado.setText("  ⏳ Analizando…");
        modeloErrores.clear();

        SwingWorker<List<RuleMatch>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RuleMatch> doInBackground() throws Exception {
                return corrector.analizar(areaTexto.getText());
            }

            @Override
            protected void done() {
                try {
                    ultimosErrores = get();

                    // Resaltar en el texto
                    StyledDocument doc = areaTexto.getStyledDocument();
                    ResaltadorErrores.aplicar(doc, ultimosErrores);

                    // Poblar lista de errores
                    if (ultimosErrores.isEmpty()) {
                        modeloErrores.addElement("✅  Sin errores detectados. ¡Texto correcto!");
                        btnCorregirTodo.setEnabled(false);
                    } else {
                        for (int i = 0; i < ultimosErrores.size(); i++) {
                            RuleMatch m = ultimosErrores.get(i);
                            String tipo = CorrectorTexto.obtenerTipoError(m);
                            String sugerencias = m.getSuggestedReplacements().isEmpty()
                                ? "(sin sugerencias)"
                                : String.join(", ", m.getSuggestedReplacements()
                                    .subList(0, Math.min(3, m.getSuggestedReplacements().size())));
                            modeloErrores.addElement(
                                String.format("[%d] %s (pos %d-%d)%n    %s%n    → %s",
                                    i + 1, tipo,
                                    m.getFromPos(), m.getToPos(),
                                    m.getMessage(),
                                    sugerencias));
                        }
                        btnCorregirTodo.setEnabled(true);
                    }

                    barraEstado.setText(String.format(
                        "  🔎 Análisis completado: %d error(es) encontrado(s).",
                        ultimosErrores.size()));

                } catch (Exception ex) {
                    barraEstado.setText("  ❌ Error durante el análisis: " + ex.getMessage());
                } finally {
                    btnAnalizar.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Corregir todo automáticamente (aplica primera sugerencia de cada error)
    // ─────────────────────────────────────────────────────────────────────────

    private void corregirTodo() {
        if (ultimosErrores == null || ultimosErrores.isEmpty()) return;

        int confirm = JOptionPane.showConfirmDialog(this,
            "Se aplicará la primera sugerencia de cada error automáticamente.\n¿Continuar?",
            "Corregir todo", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Aplicar correcciones de atrás hacia adelante para no romper posiciones
        String texto = areaTexto.getText();
        StringBuilder sb = new StringBuilder(texto);

        for (int i = ultimosErrores.size() - 1; i >= 0; i--) {
            RuleMatch m = ultimosErrores.get(i);
            if (!m.getSuggestedReplacements().isEmpty()) {
                String sugerencia = m.getSuggestedReplacements().get(0);
                sb.replace(m.getFromPos(), m.getToPos(), sugerencia);
            }
        }

        areaTexto.setText(sb.toString());
        modeloErrores.clear();
        ultimosErrores = null;
        btnCorregirTodo.setEnabled(false);
        barraEstado.setText("  ✅ Correcciones aplicadas. Vuelve a analizar para verificar.");
        // Limpiar resaltado
        StyledDocument doc = areaTexto.getStyledDocument();
        ResaltadorErrores.aplicar(doc, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Limpiar todo
    // ─────────────────────────────────────────────────────────────────────────

    private void limpiarTodo() {
        areaTexto.setText("");
        modeloErrores.clear();
        ultimosErrores = null;
        btnCorregirTodo.setEnabled(false);
        barraEstado.setText("  ✅ Motor listo. Escribe o pega texto y pulsa Analizar (F5).");
        StyledDocument doc = areaTexto.getStyledDocument();
        ResaltadorErrores.aplicar(doc, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers UI
    // ─────────────────────────────────────────────────────────────────────────

    private JButton crearBoton(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setBackground(color);
        btn.setForeground(Color.BLACK); // Cambiado a negro para mejor legibilidad
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(color.brighter()); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(color); }
        });
        return btn;
    }

    private JLabel crearLogoLabel() {
        JLabel lbl = new JLabel("📄 ValidadorTexto");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        lbl.setForeground(Color.WHITE);
        return lbl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Renderer personalizado para la lista de errores
    // ─────────────────────────────────────────────────────────────────────────

    private static class ErrorCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                new EmptyBorder(6, 8, 6, 8)));
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String texto = value.toString();
            if (texto.contains("❌")) {
                lbl.setForeground(isSelected ? Color.WHITE : new Color(160, 0, 0));
            } else if (texto.contains("⚠")) {
                lbl.setForeground(isSelected ? Color.WHITE : new Color(160, 90, 0));
            } else if (texto.contains("✅")) {
                lbl.setForeground(isSelected ? Color.WHITE : new Color(0, 120, 0));
            }
            return lbl;
        }
    }
}