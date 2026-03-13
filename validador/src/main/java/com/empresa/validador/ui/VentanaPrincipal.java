package com.empresa.validador.ui;

import com.empresa.validador.corrector.CorrectorTexto;
import org.languagetool.rules.RuleMatch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyledDocument;
import java.awt.datatransfer.StringSelection;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final JButton btnDescartar;
    private final JButton btnCortar;
    private final JButton btnCopiar;
    private final JCheckBox chkAutoCorregir;
    private final Timer temporizadorAutoCorreccion;

    // ── Motor de corrección ──────────────────────────────────────────────────
    private CorrectorTexto corrector;
    private List<RuleMatch> ultimosErrores;
    private final Set<Integer> erroresDescartados;
    private boolean cambioProgramatico;
    private boolean autoCorreccionEnCurso;

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
        erroresDescartados = new HashSet<>();
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

        listaErrores.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !chkAutoCorregir.isSelected()) {
                    descartarSeleccionActual();
                }
            }
        });

        // ── Botones ──────────────────────────────────────────────────────────
        btnAnalizar    = crearBoton("🔍 Analizar",      COLOR_BTN);
        btnLimpiar     = crearBoton("🗑 Limpiar",       COLOR_BTN);
        btnCorregirTodo = crearBoton("✅ Corregir todo", new Color(40, 130, 70));
        btnDescartar   = crearBoton("🚫 Descartar",     COLOR_BTN_DANGER);
        btnCortar      = crearBoton("✂ Cortar",        COLOR_BTN);
        btnCopiar      = crearBoton("📋 Copiar",       COLOR_BTN);
        chkAutoCorregir = new JCheckBox("Autocorregir al escribir");

        temporizadorAutoCorreccion = new Timer(900, e -> autocorregirTrasEscritura());
        temporizadorAutoCorreccion.setRepeats(false);

        btnAnalizar.setEnabled(false);
        btnLimpiar.setEnabled(false);
        btnCorregirTodo.setEnabled(false);
        btnDescartar.setEnabled(false);
        btnCortar.setEnabled(false);
        btnCopiar.setEnabled(false);
        chkAutoCorregir.setEnabled(false);

        btnAnalizar.addActionListener(e -> analizarTexto());
        btnLimpiar.addActionListener(e -> limpiarTodo());
        btnCorregirTodo.addActionListener(e -> corregirTodo());
        btnDescartar.addActionListener(e -> descartarSeleccionActual());
        btnCortar.addActionListener(e -> cortarSinFormato());
        btnCopiar.addActionListener(e -> copiarSinFormato());
        chkAutoCorregir.addActionListener(e -> {
            if (!chkAutoCorregir.isSelected()) {
                temporizadorAutoCorreccion.stop();
            }
            btnDescartar.setEnabled(false);
        });

        listaErrores.addListSelectionListener(e -> btnDescartar.setEnabled(
            !chkAutoCorregir.isSelected()
                && listaErrores.getSelectedIndex() >= 0
                && ultimosErrores != null
                && !ultimosErrores.isEmpty()
        ));

        areaTexto.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { manejarCambioTexto(); }

            @Override
            public void removeUpdate(DocumentEvent e) { manejarCambioTexto(); }

            @Override
            public void changedUpdate(DocumentEvent e) { manejarCambioTexto(); }

            private void manejarCambioTexto() {
                if (cambioProgramatico || !chkAutoCorregir.isSelected() || !areaTexto.isEnabled()) {
                    return;
                }
                temporizadorAutoCorreccion.restart();
            }
        });

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
        barraHerramientas.add(btnDescartar);
        barraHerramientas.add(btnCortar);
        barraHerramientas.add(btnCopiar);
        barraHerramientas.add(btnLimpiar);
        barraHerramientas.add(chkAutoCorregir);

        chkAutoCorregir.setOpaque(false);
        chkAutoCorregir.setForeground(Color.WHITE);
        chkAutoCorregir.setFont(new Font("SansSerif", Font.PLAIN, 12));

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
                    btnDescartar.setEnabled(false);
                    btnCortar.setEnabled(true);
                    btnCopiar.setEnabled(true);
                    chkAutoCorregir.setEnabled(true);
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
        erroresDescartados.clear();
        btnDescartar.setEnabled(false);

        SwingWorker<List<RuleMatch>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RuleMatch> doInBackground() throws Exception {
                return corrector.analizar(areaTexto.getText());
            }

            @Override
            protected void done() {
                try {
                    ultimosErrores = get();

                    // Resaltar solo errores activos (no descartados)
                    StyledDocument doc = areaTexto.getStyledDocument();
                    ResaltadorErrores.aplicar(doc, obtenerErroresActivos());

                    // Poblar lista de errores
                    if (ultimosErrores.isEmpty()) {
                        modeloErrores.addElement("✅  Sin errores detectados. ¡Texto correcto!");
                        btnCorregirTodo.setEnabled(false);
                    } else {
                        for (int i = 0; i < ultimosErrores.size(); i++) {
                            RuleMatch m = ultimosErrores.get(i);
                            modeloErrores.addElement(formatearError(i, m));
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
        int correccionesAplicadas = 0;

        for (int i = ultimosErrores.size() - 1; i >= 0; i--) {
            if (erroresDescartados.contains(i)) {
                continue;
            }
            RuleMatch m = ultimosErrores.get(i);
            if (!m.getSuggestedReplacements().isEmpty()) {
                String sugerencia = m.getSuggestedReplacements().get(0);
                sb.replace(m.getFromPos(), m.getToPos(), sugerencia);
                correccionesAplicadas++;
            }
        }

        if (correccionesAplicadas == 0) {
            barraEstado.setText("  ⚠ No hay correcciones aplicables (todas descartadas o sin sugerencia)");
            return;
        }

        setTextoSinDisparar(sb.toString());
        modeloErrores.clear();
        ultimosErrores = null;
        erroresDescartados.clear();
        btnCorregirTodo.setEnabled(false);
        btnDescartar.setEnabled(false);
        barraEstado.setText("  ✅ Correcciones aplicadas. Vuelve a analizar para verificar.");
        // Limpiar resaltado
        StyledDocument doc = areaTexto.getStyledDocument();
        ResaltadorErrores.aplicar(doc, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Limpiar todo
    // ─────────────────────────────────────────────────────────────────────────

    private void limpiarTodo() {
        setTextoSinDisparar("");
        modeloErrores.clear();
        ultimosErrores = null;
        erroresDescartados.clear();
        btnCorregirTodo.setEnabled(false);
        btnDescartar.setEnabled(false);
        barraEstado.setText("  ✅ Motor listo. Escribe o pega texto y pulsa Analizar (F5).");
        StyledDocument doc = areaTexto.getStyledDocument();
        ResaltadorErrores.aplicar(doc, List.of());
    }

    private void autocorregirTrasEscritura() {
        if (!chkAutoCorregir.isSelected() || corrector == null || autoCorreccionEnCurso) return;

        final String textoOriginal = areaTexto.getText();
        if (textoOriginal == null || textoOriginal.isBlank()) return;

        autoCorreccionEnCurso = true;
        barraEstado.setText("  ⏳ Autocorrigiendo…");

        SwingWorker<ResultadoAutoCorreccion, Void> worker = new SwingWorker<>() {
            @Override
            protected ResultadoAutoCorreccion doInBackground() throws Exception {
                List<RuleMatch> errores = corrector.analizar(textoOriginal);
                if (errores.isEmpty()) {
                    return new ResultadoAutoCorreccion(textoOriginal, 0);
                }

                StringBuilder sb = new StringBuilder(textoOriginal);
                int correcciones = 0;
                for (int i = errores.size() - 1; i >= 0; i--) {
                    RuleMatch m = errores.get(i);
                    if (!m.getSuggestedReplacements().isEmpty()) {
                        String sugerencia = m.getSuggestedReplacements().get(0);
                        sb.replace(m.getFromPos(), m.getToPos(), sugerencia);
                        correcciones++;
                    }
                }

                return new ResultadoAutoCorreccion(sb.toString(), correcciones);
            }

            @Override
            protected void done() {
                try {
                    ResultadoAutoCorreccion resultado = get();
                    if (!resultado.textoCorregido.equals(textoOriginal)) {
                        setTextoSinDisparar(resultado.textoCorregido);
                    }
                    modeloErrores.clear();
                    ultimosErrores = null;
                    erroresDescartados.clear();
                    btnCorregirTodo.setEnabled(false);
                    btnDescartar.setEnabled(false);
                    StyledDocument doc = areaTexto.getStyledDocument();
                    ResaltadorErrores.aplicar(doc, List.of());

                    if (resultado.correcciones > 0) {
                        barraEstado.setText("  ✅ Autocorrección aplicada: " + resultado.correcciones + " cambio(s).");
                    } else {
                        barraEstado.setText("  ✅ Sin cambios automáticos necesarios.");
                    }
                } catch (Exception ex) {
                    barraEstado.setText("  ❌ Error en autocorrección: " + ex.getMessage());
                } finally {
                    autoCorreccionEnCurso = false;
                }
            }
        };
        worker.execute();
    }

    private void setTextoSinDisparar(String texto) {
        cambioProgramatico = true;
        try {
            areaTexto.setText(texto);
        } finally {
            cambioProgramatico = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Copiar texto plano al portapapeles
    // ─────────────────────────────────────────────────────────────────────────

    private void copiarSinFormato() {
        String textoSeleccionado = areaTexto.getSelectedText();
        String textoACopiar = (textoSeleccionado != null && !textoSeleccionado.isEmpty())
            ? textoSeleccionado
            : areaTexto.getText();

        if (textoACopiar == null || textoACopiar.isBlank()) {
            barraEstado.setText("  ⚠ No hay texto para copiar.");
            return;
        }

        StringSelection contenido = new StringSelection(textoACopiar);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contenido, null);
        barraEstado.setText("  📋 Texto copiado sin formato al portapapeles.");
    }

    private void cortarSinFormato() {
        String textoSeleccionado = areaTexto.getSelectedText();
        String textoACopiar = (textoSeleccionado != null && !textoSeleccionado.isEmpty())
            ? textoSeleccionado
            : areaTexto.getText();

        if (textoACopiar == null || textoACopiar.isBlank()) {
            barraEstado.setText("  ⚠ No hay texto para cortar.");
            return;
        }

        StringSelection contenido = new StringSelection(textoACopiar);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contenido, null);

        // Corta "todo": limpia editor y panel de resultados para empezar de cero.
        limpiarTodo();
        barraEstado.setText("  ✂ Texto cortado sin formato y editor limpio.");
    }

    private String formatearError(int indice, RuleMatch m) {
        String tipo = CorrectorTexto.obtenerTipoError(m);
        String sugerencias = m.getSuggestedReplacements().isEmpty()
            ? "(sin sugerencias)"
            : String.join(", ", m.getSuggestedReplacements()
                .subList(0, Math.min(3, m.getSuggestedReplacements().size())));
        String estado = erroresDescartados.contains(indice) ? " [DESCARTADO]" : "";

        return String.format("[%d]%s %s (pos %d-%d)%n    %s%n    → %s",
            indice + 1, estado, tipo,
            m.getFromPos(), m.getToPos(),
            m.getMessage(),
            sugerencias);
    }

    private void descartarSeleccionActual() {
        if (chkAutoCorregir.isSelected() || ultimosErrores == null || ultimosErrores.isEmpty()) {
            return;
        }

        int idx = listaErrores.getSelectedIndex();
        if (idx < 0 || idx >= ultimosErrores.size()) {
            barraEstado.setText("  ⚠ Selecciona un error para descartar o recuperar.");
            return;
        }

        if (erroresDescartados.contains(idx)) {
            erroresDescartados.remove(idx);
            barraEstado.setText("  ✅ Corrección recuperada para el error seleccionado.");
        } else {
            erroresDescartados.add(idx);
            barraEstado.setText("  🚫 Corrección descartada para el error seleccionado.");
        }

        modeloErrores.set(idx, formatearError(idx, ultimosErrores.get(idx)));
        listaErrores.setSelectedIndex(idx);

        // Al descartar, deja de marcar ese error en rojo inmediatamente.
        StyledDocument doc = areaTexto.getStyledDocument();
        ResaltadorErrores.aplicar(doc, obtenerErroresActivos());
    }

    private List<RuleMatch> obtenerErroresActivos() {
        if (ultimosErrores == null || ultimosErrores.isEmpty()) {
            return List.of();
        }

        List<RuleMatch> activos = new ArrayList<>();
        for (int i = 0; i < ultimosErrores.size(); i++) {
            if (!erroresDescartados.contains(i)) {
                activos.add(ultimosErrores.get(i));
            }
        }
        return activos;
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

    private static class ResultadoAutoCorreccion {
        private final String textoCorregido;
        private final int correcciones;

        private ResultadoAutoCorreccion(String textoCorregido, int correcciones) {
            this.textoCorregido = textoCorregido;
            this.correcciones = correcciones;
        }
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