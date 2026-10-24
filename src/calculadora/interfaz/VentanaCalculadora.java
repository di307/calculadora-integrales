package calculadora.interfaz; // Paquete donde vive nuestra interfaz gráfica

import calculadora.logica.HistorialPasos; // Importo el contenedor de resultados y pasos
import calculadora.logica.HistorialPasos.PasoExplicado; // Importo el registro de cada paso individual
import calculadora.logica.ResolvedorCalculo; // Importo el coordinador matemático
import calculadora.logica.ResolvedorCalculo.TipoOperacion; // Importo los modos (Derivada, Indefinida, Definida)
import calculadora.logica.TerminoPolinomio; // Importo el formateador de números

import javax.swing.*; // Componentes visuales de Swing (JFrame, JPanel, JButton, etc.)
import javax.swing.border.EmptyBorder; // Para dar márgenes internos limpios
import javax.swing.event.DocumentEvent; // Evento que detecta cuando escriben o borran texto
import javax.swing.event.DocumentListener; // Escuchador de cambios en las cajas de texto
import javax.swing.filechooser.FileNameExtensionFilter; // Filtro para guardar únicamente archivos .jpg
import javax.swing.plaf.basic.BasicButtonUI; // Quita los estilos viejos de Windows en los botones
import javax.swing.plaf.basic.BasicComboBoxUI; // Quita los bordes blancos del JComboBox
import javax.swing.plaf.basic.BasicScrollBarUI; // Permite pintar la barra de scroll delgada y verde
import java.awt.*; // Layouts, colores, dimensiones y cursores
import java.awt.event.FocusAdapter; // Para rastrear qué caja de texto tiene seleccionado el usuario
import java.awt.event.FocusEvent; // Evento de ganancia o pérdida de foco
import java.awt.event.MouseAdapter; // Para iluminar los botones cuando pasa el mouse por encima
import java.awt.event.MouseEvent; // Evento del mouse
import java.io.File; // Manejo de archivo al exportar la gráfica
import java.util.function.DoubleUnaryOperator; // Representa la función matemática evaluable
import java.util.regex.Matcher; // Buscador de patrones regex para superíndices
import java.util.regex.Pattern; // Compilador de expresiones regulares
import java.util.concurrent.CancellationException; // Excepción cuando se cancela un cálculo anterior
import java.util.concurrent.ExecutionException; // Excepción cuando el hilo de cálculo reporta un error matemático

public class VentanaCalculadora extends JFrame { // Clase principal de la ventana de nuestra calculadora

    private final ResolvedorCalculo resolvedor = new ResolvedorCalculo(); // Instancia de nuestro coordinador de cálculo
    private final GraficadorFuncion graficador = new GraficadorFuncion(); // Instancia del lienzo graficador

    private final JTextField campoEcuacion = new JTextField("2x + 4y"); // Caja donde el usuario escribe la función
    private final JTextField campoLimA = new JTextField("2"); // Caja para el límite inferior 'a'
    private final JTextField campoLimB = new JTextField("4"); // Caja para el límite superior 'b'
    private JTextField campoEnFoco = campoEcuacion; // Guarda en cuál de las 3 cajas está parado el cursor

    private final JComboBox<TipoOperacion> comboOperacion = new JComboBox<>(TipoOperacion.values()); // Selector de operación
    private final JComboBox<String> comboVariable = new JComboBox<>(new String[]{"x", "y"}); // Selector de variable activa (x o y)

    private final JLabel lblKpiResultado = new JLabel("-"); // Etiqueta grande para el resultado principal
    private final JLabel lblKpiIntegral = new JLabel("-"); // Etiqueta para el valor de la integral definida
    private final JLabel lblKpiPendiente = new JLabel("-"); // Etiqueta para la derivada evaluada en 1
    private final JLabel lblKpiPasos = new JLabel("-"); // Contador interno de pasos
    private final JLabel lblVariableActual = new JLabel("Respecto de: x"); // Indicador visual de la variable activa
    private long versionCalculo = 0; // Contador para descartar cálculos viejos si el usuario teclea rápido
    private SwingWorker<ResultadoCalculo, Void> trabajadorCalculo; // Hilo en segundo plano para no trabar la ventana
    private final JLabel lblEstado = new JLabel("● Esperando entrada..."); // Barra de estado inferior

    private final JPanel panelLimites = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); // Panel que agrupa a: y b:
    private final ScrollablePanel contenedorPasos = new ScrollablePanel(); // Panel vertical donde se apilan los pasos

    // Temporizador de 400ms que espera a que termines de escribir antes de lanzar el cálculo
    private final Timer timerCalculo = new Timer(400, e -> calcularYActualizarInmediato());

    // Paleta de colores oficial del proyecto
    private static final Color BG_APP = new Color(0x0F0F0F); // Negro profundo de fondo general
    private static final Color BG_PANEL = new Color(0x202020); // Gris carbón para las tarjetas
    private static final Color VERDE_NEON = new Color(0x5DD62C); // Verde neón para acentos y resultados
    private static final Color VERDE_OSCURO = new Color(0x337418); // Verde oscuro para bordes activos
    private static final Color TEXTO_BLANCO = new Color(0xF8F8F8); // Blanco suave para lectura cómoda
    
    private static final Font F_CONSOLA = new Font("Consolas", Font.BOLD, 15); // Fuente estilo código para controles
    private static final Color BG_BOTON = new Color(0x303030); // Color para botones de borrado
    private static final Color BG_BOTON_FUNCION = new Color(0x262626); // Color para botones de funciones matemáticas
    private static final Color BG_BOTON_OPERADOR = new Color(0x3A4A32); // Color verde grisáceo para operadores

    public VentanaCalculadora() { // Constructor que configura e inicia toda la ventana
        super("Calculadora de Integrales y Derivadas"); // Título en la barra superior de la ventana
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // Cierra el programa por completo al darle a la X
        
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Abre la calculadora en pantalla completa desde el inicio
        setMinimumSize(new Dimension(1060, 720)); // Tamaño mínimo por si el usuario restaura la ventana
        setLocationRelativeTo(null); // Centra la ventana en el monitor
        timerCalculo.setRepeats(false); // El timer solo debe dispararse una vez por cada pausa al escribir

        comboOperacion.setSelectedItem(TipoOperacion.DERIVADA); // Inicio en modo Derivada para lucir el ejemplo 2x + 4y

        armarPantalla(); // Construyo y acomodo todos los paneles visuales
        conectarEventosEnVivo(); // Conecto los escuchadores de teclado y combos
        actualizarEnVivo(); // Realizo el primer cálculo inicial

        // Abro la Guía de Usuario automáticamente un instante después de pintar la pantalla completa
        SwingUtilities.invokeLater(this::mostrarManualUsuario);
    }

    // Divide la ventana en la barra lateral izquierda y las dos columnas centrales
    private void armarPantalla() {
        JPanel raiz = new JPanel(new BorderLayout()); // Panel contenedor raíz
        raiz.setBackground(BG_APP); // Fondo negro
        raiz.add(crearBarraLateral(), BorderLayout.WEST); // Pego la barra de íconos a la izquierda

        JPanel centro = new JPanel(new GridLayout(1, 2, 16, 0)); // Divido el centro en 2 columnas iguales con 16px de separación
        centro.setBackground(BG_APP); // Fondo negro
        centro.setBorder(new EmptyBorder(16, 16, 16, 16)); // Margen exterior de 16 píxeles alrededor
        centro.add(crearColumnaControles()); // Columna izquierda: KPIs, entrada y teclado
        centro.add(crearColumnaResultados()); // Columna derecha: Gráfica y procedimiento paso a paso

        raiz.add(centro, BorderLayout.CENTER); // Meto las dos columnas en el centro de la ventana
        setContentPane(raiz); // Establezco el panel raíz como contenido de la ventana
    }

    // Crea la barra vertical izquierda con el logo de integral, el botón JPG y el botón del Manual
    private JPanel crearBarraLateral() {
        JPanel barra = new JPanel();
        barra.setBackground(BG_APP);
        barra.setPreferredSize(new Dimension(68, 100)); // Ancho fijo de 68 píxeles
        barra.setLayout(new BoxLayout(barra, BoxLayout.Y_AXIS)); // Apilo los botones de arriba hacia abajo
        barra.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BG_PANEL)); // Línea divisoria sutil a la derecha

        JLabel icono = new JLabel("∫", SwingConstants.CENTER); // Símbolo de integral como logo de la app
        icono.setFont(new Font("Serif", Font.BOLD, 36));
        icono.setForeground(VERDE_NEON);
        icono.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnExportar = botonLateral("JPG", "Exportar Gráfica"); // Botón para guardar foto de la gráfica
        btnExportar.addActionListener(e -> exportarGrafica()); // Conecto el clic con el método de guardado

        JButton btnManual = botonLateral("?", "Manual"); // Botón para volver a abrir el instructivo cuando quiera
        btnManual.addActionListener(e -> mostrarManualUsuario()); // Conecto el clic con el modal de ayuda

        barra.add(Box.createVerticalStrut(20)); // Espacio vacío arriba
        barra.add(icono); // Agrego el logo
        barra.add(Box.createVerticalStrut(40)); // Espacio debajo del logo
        barra.add(btnExportar); // Agrego botón JPG
        barra.add(Box.createVerticalStrut(15)); // Separación entre botones
        barra.add(btnManual); // Agrego botón de ayuda "?"
        barra.add(Box.createVerticalGlue()); // Empujo todo hacia arriba
        return barra;
    }

    // Fabrica un botón cuadrado estilizado para la barra lateral
    private JButton botonLateral(String texto, String tooltip) {
        JButton b = new JButton(texto);
        b.setUI(new BasicButtonUI()); // Quito el diseño por defecto de Windows
        b.setToolTipText(tooltip); // Mensaje flotante al dejar el mouse encima
        b.setFont(new Font("Consolas", Font.BOLD, 12));
        b.setForeground(TEXTO_BLANCO);
        b.setBackground(BG_PANEL);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(0x333333), 1));
        b.setMaximumSize(new Dimension(46, 46)); // Tamaño cuadrado fijo de 46x46
        b.setPreferredSize(new Dimension(46, 46));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // Cursor de manita al pasar encima
        
        b.addMouseListener(new MouseAdapter() { // Efecto hover al pasar el ratón
            public void mouseEntered(MouseEvent e) { b.setForeground(VERDE_NEON); b.setBorder(BorderFactory.createLineBorder(VERDE_OSCURO, 1)); }
            public void mouseExited(MouseEvent e) { b.setForeground(TEXTO_BLANCO); b.setBorder(BorderFactory.createLineBorder(new Color(0x333333), 1)); }
        });
        return b;
    }

    // Construye la mitad izquierda de la pantalla (Tarjetas de resultados arriba y panel de escritura abajo)
    private JPanel crearColumnaControles() {
        JPanel col = new JPanel(new BorderLayout(0, 14));
        col.setOpaque(false);

        JPanel kpis = new JPanel(); // Contenedor superior de tarjetas de indicadores
        kpis.setOpaque(false);
        kpis.setLayout(new BoxLayout(kpis, BoxLayout.Y_AXIS));
        JPanel tarjetaResultado = tarjetaResultadoPrincipal(); // Tarjeta grande donde sale la respuesta principal
        tarjetaResultado.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpis.add(tarjetaResultado);
        kpis.add(Box.createVerticalStrut(10));
        
        JPanel secundarios = new JPanel(new GridLayout(1, 2, 12, 0)); // Fila con las 2 tarjetas pequeñas de abajo
        secundarios.setOpaque(false);
        secundarios.add(tarjetaIndicador("INTEGRAL DEFINIDA", lblKpiIntegral, TEXTO_BLANCO));
        secundarios.add(tarjetaIndicador("f′(1) (APROX.)", lblKpiPendiente, VERDE_NEON));
        secundarios.setAlignmentX(Component.LEFT_ALIGNMENT);
        secundarios.setPreferredSize(new Dimension(10, 78));
        secundarios.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        kpis.add(secundarios);

        JPanel panelEntrada = crearTarjetaBase(); // Tarjeta donde va el campo de ecuación y el teclado
        panelEntrada.setLayout(new BoxLayout(panelEntrada, BoxLayout.Y_AXIS));

        JLabel sub = new JLabel("INGRESA TU FUNCIÓN");
        sub.setFont(new Font("Consolas", Font.BOLD, 12));
        sub.setForeground(Color.GRAY);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        campoEcuacion.setFont(new Font("Cambria Math", Font.PLAIN, 25)); // Fuente matemática grande para escribir cómodo
        campoEcuacion.setBackground(BG_APP); 
        campoEcuacion.setForeground(TEXTO_BLANCO);
        campoEcuacion.setCaretColor(VERDE_NEON); // Cursor parpadeante en verde neón
        campoEcuacion.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VERDE_OSCURO, 2),
                new EmptyBorder(10, 12, 10, 12)
        ));
        campoEcuacion.setToolTipText("Puedes combinar x e y (ej. 2x + 4y) y elegir abajo respecto a cuál variable operar.");
        campoEcuacion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        campoEcuacion.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filaConfig = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); // Fila con los selectores de Variable y Operación
        filaConfig.setOpaque(false);
        filaConfig.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        estilizarComboBox(comboOperacion); // Pinto el selector de operación en tema oscuro
        estilizarComboBox(comboVariable); // Pinto el selector de variable (x / y) en tema oscuro
        comboVariable.setSelectedItem("x"); // Por defecto arrancamos operando respecto a x

        panelLimites.setOpaque(false); // Panel que contiene las cajitas a: y b:
        estilizarCampoLimite(campoLimA); estilizarCampoLimite(campoLimB);
        panelLimites.add(etiqueta("a:"));
        panelLimites.add(campoLimA);
        panelLimites.add(etiqueta("b:"));
        panelLimites.add(campoLimB);

        filaConfig.add(etiqueta("Variable:"));
        filaConfig.add(comboVariable);
        lblVariableActual.setForeground(VERDE_NEON);
        lblVariableActual.setFont(new Font("Consolas", Font.BOLD, 12));
        lblVariableActual.setText("Respecto de: x");
        filaConfig.add(lblVariableActual);
        filaConfig.add(comboOperacion);
        panelLimites.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel tecladoRapido = crearTecladoEstiloModerno(); // Creo la botonera matemática cuadrada
        tecladoRapido.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblEstado.setFont(F_CONSOLA); // Etiqueta que avisa si el cálculo está listo o si hubo error
        lblEstado.setForeground(VERDE_NEON);
        lblEstado.setAlignmentX(Component.LEFT_ALIGNMENT);

        panelEntrada.add(sub);
        panelEntrada.add(Box.createVerticalStrut(10));
        panelEntrada.add(campoEcuacion);
        panelEntrada.add(Box.createVerticalStrut(12));
        panelEntrada.add(filaConfig);
        panelEntrada.add(Box.createVerticalStrut(8));
        panelEntrada.add(panelLimites);
        panelEntrada.add(Box.createVerticalStrut(12));
        panelEntrada.add(tecladoRapido);
        panelEntrada.add(Box.createVerticalStrut(15));
        panelEntrada.add(lblEstado);
        panelEntrada.add(Box.createVerticalGlue()); 

        col.add(kpis, BorderLayout.NORTH);
        col.add(panelEntrada, BorderLayout.CENTER);
        return col;
    }

    // Personaliza los JComboBox para quitarles el fondo blanco nativo de Java y adaptarlos al tema oscuro
    private void estilizarComboBox(JComboBox<?> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() { // Botón de la flechita desplegable
                JButton btn = new JButton("▼");
                btn.setUI(new BasicButtonUI());
                btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 12));
                btn.setForeground(TEXTO_BLANCO);
                btn.setBackground(BG_PANEL);
                btn.setOpaque(true);
                btn.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                btn.setFocusPainted(false);
                return btn;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(BG_PANEL); // Pinto el fondo del valor seleccionado en gris carbón
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) comboBox.getRenderer();
                Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
                c.setBackground(BG_PANEL);
                c.setForeground(TEXTO_BLANCO);
                c.setFont(comboBox.getFont());
                currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height,
                        c instanceof JPanel);
            }
        });
        combo.setBackground(BG_PANEL);
        combo.setForeground(TEXTO_BLANCO);
        combo.setFont(F_CONSOLA);
        combo.setOpaque(true);
        combo.setFocusable(false);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x444444), 1),
                new EmptyBorder(2, 7, 2, 5)));
        combo.setPreferredSize(new Dimension(combo == comboVariable ? 78 : 225, 34));
        combo.setMinimumSize(new Dimension(combo == comboVariable ? 78 : 190, 34));
        combo.setRenderer(new DefaultListCellRenderer() { // Pinto cada opción de la lista desplegable
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? VERDE_OSCURO : BG_PANEL); // Verde oscuro al pasar encima, gris en reposo
                c.setForeground(TEXTO_BLANCO);
                c.setFont(F_CONSOLA);
                if (c instanceof JComponent jc) jc.setOpaque(true);
                return c;
            }
        });
    }

    // Construye la cuadrícula de botones matemáticos incluyendo las variables x e y
    private JPanel crearTecladoEstiloModerno() {
        JPanel envoltura = new JPanel(new BorderLayout(0, 7));
        envoltura.setOpaque(false);
        JLabel encabezado = new JLabel("TECLADO MATEMÁTICO");
        encabezado.setFont(new Font("Consolas", Font.BOLD, 11));
        encabezado.setForeground(Color.GRAY);
        envoltura.add(encabezado, BorderLayout.NORTH);

        JPanel teclado = new JPanel(new GridLayout(4, 6, 7, 7)); // Cuadrícula de 4 filas por 6 columnas
        teclado.setOpaque(false);
        teclado.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        String[] botones = { // Lista de símbolos disponibles en el teclado táctico
            "x", "y", "(", ")", "⌫", "C",
            "x²", "xⁿ", "√", "π", "e", "÷",
            "sen", "cos", "tan", "ln", "log", "×",
            "abs", "eˣ", "a/b", "∞", "−∞", "+"
        };

        for (String simb : botones) { // Creo cada botón de la lista
            JButton b = new JButton(simb);
            b.setUI(new BasicButtonUI()); // Estilo plano sin relieves de Windows
            b.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16));
            b.setForeground(TEXTO_BLANCO);
            b.setBackground(esOperador(simb) ? BG_BOTON_OPERADOR
                    : (simb.equals("C") || simb.equals("⌫") ? BG_BOTON : BG_BOTON_FUNCION));
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x414141), 1),
                    new EmptyBorder(7, 3, 7, 3)));
            b.setFocusable(false); // Evita que el botón robe el cursor cuando escribes en los límites a o b
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText(descripcionBoton(simb));
            b.addMouseListener(new MouseAdapter() { // Ilumino el botón en verde cuando entra el cursor
                @Override public void mouseEntered(MouseEvent e) {
                    b.setBackground(new Color(0x46583D));
                    b.setBorder(BorderFactory.createLineBorder(VERDE_NEON, 1));
                }
                @Override public void mouseExited(MouseEvent e) {
                    b.setBackground(esOperador(simb) ? BG_BOTON_OPERADOR
                            : (simb.equals("C") || simb.equals("⌫") ? BG_BOTON : BG_BOTON_FUNCION));
                    b.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0x414141), 1),
                            new EmptyBorder(7, 3, 7, 3)));
                }
            });
            b.addActionListener(e -> insertarSimbolo(simb)); // Inserto el símbolo al hacer clic
            teclado.add(b);
        }
        envoltura.add(teclado, BorderLayout.CENTER);
        envoltura.setAlignmentX(Component.LEFT_ALIGNMENT);
        return envoltura;
    }

    private boolean esOperador(String s) {
        return "+−×÷·/".contains(s) || s.equals("a/b");
    }

    private String descripcionBoton(String s) {
        return switch (s) {
            case "x" -> "Insertar variable x";
            case "y" -> "Insertar variable y";
            case "x²" -> "Elevar al cuadrado";
            case "xⁿ" -> "Elevar a una potencia";
            case "√" -> "Insertar raíz cuadrada";
            case "a/b" -> "Insertar una fracción";
            case "⌫" -> "Borrar el carácter anterior";
            case "C" -> "Borrar toda la expresión";
            case "eˣ" -> "Función exponencial";
            case "∞" -> "Insertar infinito positivo (para límites)";
            case "−∞" -> "Insertar infinito negativo (para límites)";
            default -> "Insertar " + s;
        };
    }

    // Inserta el símbolo del botón presionado exactamente en la caja de texto donde esté el cursor
    private void insertarSimbolo(String simb) {
        Component focoActual = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focoActual instanceof JTextField campo
                && (campo == campoEcuacion || campo == campoLimA || campo == campoLimB)) {
            campoEnFoco = campo; // Actualizo cuál es la caja activa
        }
        if (simb.equals("∞") || simb.equals("−∞")) { // Si presionó infinito
            if (campoEnFoco == campoLimA || campoEnFoco == campoLimB) { // Si estaba parado en un límite
                campoEnFoco.setText(simb); // Pongo el infinito directo en ese límite
                campoEnFoco.setCaretPosition(simb.length());
            } else {
                campoEnFoco.replaceSelection(simb);
            }
            campoEnFoco.requestFocusInWindow();
            return;
        }
        if (simb.equals("C")) { // El botón C limpia la caja activa
            campoEnFoco.setText("");
            return;
        }
        if (simb.equals("⌫")) { // El botón borrar elimina el carácter a la izquierda del cursor
            String txt = campoEnFoco.getText();
            int pos = campoEnFoco.getCaretPosition();
            if (pos > 0) {
                campoEnFoco.setText(txt.substring(0, pos - 1) + txt.substring(pos));
                campoEnFoco.setCaretPosition(pos - 1);
            }
            campoEnFoco.requestFocusInWindow();
            return;
        }

        String insercion = switch (simb) { // Traduzco el botón al texto que se inserta
            case "x²" -> "²";
            case "xⁿ" -> "^()";
            case "√" -> "√()";
            case "sen", "cos", "tan", "ln", "log", "abs" -> simb + "()";
            case "eˣ" -> "e^()";
            case "a/b" -> "()/()";
            case "×", "·" -> "×";
            case "÷" -> "÷";
            case "−" -> "−";
            default -> simb;
        };
        campoEnFoco.replaceSelection(insercion); // Meto el texto en la posición actual del cursor
        if (simb.equals("√") || simb.matches("sen|cos|tan|ln|log|abs") || simb.equals("eˣ") || simb.equals("xⁿ")) {
            campoEnFoco.setCaretPosition(Math.max(0, campoEnFoco.getCaretPosition() - 1)); // Meto el cursor adentro del paréntesis
        } else if (simb.equals("a/b")) {
            campoEnFoco.setCaretPosition(Math.max(0, campoEnFoco.getCaretPosition() - 4)); // Pongo el cursor en el numerador
        }
        campoEnFoco.requestFocusInWindow();
    }

    // Construye la mitad derecha de la pantalla (Gráfica arriba y Solución paso a paso abajo)
    private JPanel crearColumnaResultados() {
        JPanel col = new JPanel(new GridLayout(2, 1, 0, 14));
        col.setOpaque(false);

        JPanel tarjetaGrafica = crearTarjetaBase();
        tarjetaGrafica.setLayout(new BorderLayout(0, 8));
        JLabel titGraf = new JLabel("ANÁLISIS GRÁFICO (VERDE: f · GRIS: f' APROX.)");
        titGraf.setFont(new Font("Consolas", Font.BOLD, 12));
        titGraf.setForeground(Color.GRAY);
        tarjetaGrafica.add(titGraf, BorderLayout.NORTH);
        tarjetaGrafica.add(graficador, BorderLayout.CENTER);

        JPanel tarjetaPasos = crearTarjetaBase();
        tarjetaPasos.setLayout(new BorderLayout(0, 8));
        JLabel titPasos = new JLabel("SOLUCIÓN PASO A PASO");
        titPasos.setFont(new Font("Consolas", Font.BOLD, 12));
        titPasos.setForeground(Color.GRAY);

        contenedorPasos.setLayout(new BoxLayout(contenedorPasos, BoxLayout.Y_AXIS));
        contenedorPasos.setBackground(BG_PANEL);

        JScrollPane scroll = new JScrollPane(contenedorPasos); // Contenedor con barra de desplazamiento vertical
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PANEL);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // Prohibido el scroll horizontal
        scroll.getVerticalScrollBar().setUnitIncrement(16); // Velocidad suave al girar la rueda del ratón
        
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() { // Diseño minimalista de 6px para la barra vertical
            @Override public Dimension getPreferredSize(JComponent c) {
                return new Dimension(6, super.getPreferredSize(c).height); 
            }
            @Override protected void configureScrollBarColors() {
                this.thumbColor = VERDE_NEON; // Barra que se mueve en color verde neón
                this.trackColor = BG_APP; // Carril de fondo en negro
            }
            @Override protected JButton createDecreaseButton(int orientation) { return createBotonCero(); }
            @Override protected JButton createIncreaseButton(int orientation) { return createBotonCero(); }
            private JButton createBotonCero() { // Oculto las flechitas feas de arriba y abajo del scroll
                JButton jb = new JButton(); jb.setPreferredSize(new Dimension(0, 0)); return jb;
            }
        });

        tarjetaPasos.add(titPasos, BorderLayout.NORTH);
        tarjetaPasos.add(scroll, BorderLayout.CENTER);

        col.add(tarjetaGrafica);
        col.add(tarjetaPasos);
        return col;
    }

    // Conecta los eventos para que cualquier cambio en la ecuación, variable o límites recalcule en vivo
    private void conectarEventosEnVivo() {
        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { actualizarEnVivo(); }
            @Override public void removeUpdate(DocumentEvent e) { actualizarEnVivo(); }
            @Override public void changedUpdate(DocumentEvent e) { actualizarEnVivo(); }
        };
        campoEcuacion.getDocument().addDocumentListener(dl);
        campoLimA.getDocument().addDocumentListener(dl);
        campoLimB.getDocument().addDocumentListener(dl);

        panelLimites.setVisible(comboOperacion.getSelectedItem() == TipoOperacion.INTEGRAL_DEFINIDA);
        comboOperacion.addActionListener(e -> { // Cuando cambia entre Derivada, Indefinida o Definida
            TipoOperacion modo = (TipoOperacion) comboOperacion.getSelectedItem();
            panelLimites.setVisible(modo == TipoOperacion.INTEGRAL_DEFINIDA); // Solo muestro límites [a, b] en Definida
            revalidate();
            actualizarEnVivo();
        });
        comboVariable.addActionListener(e -> { // Cuando cambia entre variable 'x' y variable 'y'
            String elegida = obtenerVariable();
            lblVariableActual.setText("Respecto de: " + elegida); // Actualizo el texto indicador
            actualizarEnVivo(); // Recalculo toda la derivada o integral respecto a la nueva variable
        });
        
        FocusAdapter tracker = new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                campoEnFoco = (JTextField) e.getSource(); // Guardo cuál caja acaba de recibir el clic
            }
            @Override public void focusLost(FocusEvent e) {
                if (e.getSource() == campoEcuacion) { // Cuando sale de la caja de ecuación, convierto ^2 a ²
                    SwingUtilities.invokeLater(() -> {
                        String bonita = embellecerEntrada(campoEcuacion.getText());
                        if (!bonita.equals(campoEcuacion.getText())) {
                            int posicion = campoEcuacion.getCaretPosition();
                            campoEcuacion.setText(bonita);
                            campoEcuacion.setCaretPosition(Math.min(posicion, bonita.length()));
                        }
                    });
                }
            }
        };
        campoEcuacion.addFocusListener(tracker);
        campoLimA.addFocusListener(tracker);
        campoLimB.addFocusListener(tracker);
    }

    private void actualizarEnVivo() {
        if (trabajadorCalculo != null && !trabajadorCalculo.isDone()) trabajadorCalculo.cancel(true); // Cancelo el hilo anterior si seguía corriendo
        versionCalculo++;
        timerCalculo.restart(); // Reinicio la espera de 400ms
    }

    private String obtenerVariable() {
        Object seleccion = comboVariable.getSelectedItem();
        return seleccion == null ? "x" : seleccion.toString();
    }

    // Embellece las expresiones internas cambiando asteriscos, divisiones y potencias a símbolos matemáticos reales
    private String traducirAFormatoMatematico(String texto) {
        if (texto == null) return "";
        String res = texto.replace("sqrt", "√").replace("pi", "π")
                .replace("sin(", "sen(").replace(" * ", " · ").replace("*", " · ")
                .replace("f'(", "f′(").replace("u'", "u′").replace("v'", "v′")
                .replace("-", "−").replace("/", " ÷ ");
        Matcher m = Pattern.compile("\\^\\(?(-?\\d+(?:\\.\\d+)?)\\)?").matcher(res); // Busco exponentes numéricos
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String exp = m.group(1);
            String sup = aSuperindice(exp); // Los transformo a caracteres superíndice Unicode
            m.appendReplacement(sb, Matcher.quoteReplacement(sup == null ? "^" + exp : sup));
        }
        m.appendTail(sb);
        String[] lineas = sb.toString().split("\\R", -1);
        for (int i = 0; i < lineas.length; i++) {
            lineas[i] = lineas[i].replaceAll("[ \\t]+", " ").trim();
        }
        res = String.join("\n", lineas);
        res = res.replaceAll("(?<![\\d.])([0-9]+) · ([xy])\\b", "$1$2")
                .replaceAll("([0-9]+) · \\(", "$1(")
                .replaceAll("([xy]) · \\(", "$1(")
                .replaceAll("\\) · \\(", ")(")
                .replace("·", " ");
        return res;
    }

    private String embellecerEntrada(String texto) {
        if (texto == null || texto.isBlank()) return texto == null ? "" : texto;
        String r = texto.replace("sqrt", "√").replace("pi", "π")
                .replace("sin(", "sen(").replace("*", "×").replace("/", "÷")
                .replace("-", "−");
        Matcher m = Pattern.compile("\\^([0-9]+)").matcher(r);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String sup = aSuperindice(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(sup == null ? m.group() : sup));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String aSuperindice(String valor) { // Traduce cada dígito a su carácter superíndice Unicode
        StringBuilder r = new StringBuilder();
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '0' -> r.append('⁰'); case '1' -> r.append('¹'); case '2' -> r.append('²');
                case '3' -> r.append('³'); case '4' -> r.append('⁴'); case '5' -> r.append('⁵');
                case '6' -> r.append('⁶'); case '7' -> r.append('⁷'); case '8' -> r.append('⁸');
                case '9' -> r.append('⁹'); case '-' -> r.append('⁻'); case '.' -> r.append('·');
                default -> { return null; }
            }
        }
        return r.toString();
    }

    private record ResultadoCalculo(HistorialPasos historial, DoubleUnaryOperator funcion,
                                    TipoOperacion modo, Double limiteA, Double limiteB,
                                    String variable, String funcionOriginal) {}

    // Ejecuta el cálculo matemático en un hilo SwingWorker separado para que la interfaz nunca se congele
    private void calcularYActualizarInmediato() {
        String ecuacion = campoEcuacion.getText().trim();
        String v = obtenerVariable();
        TipoOperacion modo = (TipoOperacion) comboOperacion.getSelectedItem();
        String strA = campoLimA.getText().trim();
        String strB = campoLimB.getText().trim();
        long estaVersion = ++versionCalculo;

        if (trabajadorCalculo != null && !trabajadorCalculo.isDone()) {
            trabajadorCalculo.cancel(true);
        }

        if (ecuacion.isEmpty()) { // Si borraron toda la ecuación, limpio las tarjetas y la gráfica
            lblKpiResultado.setText("—");
            lblKpiIntegral.setText("—");
            lblKpiPendiente.setText("—");
            lblKpiPasos.setText("—");
            lblEstado.setForeground(Color.GRAY);
            lblEstado.setText("● Escribe una función");
            graficador.actualizarGrafica(x -> Double.NaN, v, null, null, false);
            contenedorPasos.removeAll();
            contenedorPasos.revalidate();
            contenedorPasos.repaint();
            return;
        }

        lblEstado.setForeground(new Color(0xC9C9C9));
        lblEstado.setText("● Calculando…");
        trabajadorCalculo = new SwingWorker<>() {
            @Override protected ResultadoCalculo doInBackground() { // Esto corre en segundo plano fuera del hilo visual
                HistorialPasos hist = resolvedor.resolver(ecuacion, v, modo, strA, strB);
                if (isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                DoubleUnaryOperator fn = resolvedor.convertirAFuncion(ecuacion, v);
                Double a = null, b = null;
                if (modo == TipoOperacion.INTEGRAL_DEFINIDA) {
                    a = resolvedor.leerLimite(strA, "inferior (a)");
                    b = resolvedor.leerLimite(strB, "superior (b)");
                }
                return new ResultadoCalculo(hist, fn, modo, a, b, v, ecuacion);
            }

            @Override protected void done() { // Cuando el hilo termina, actualizo las etiquetas y la gráfica
                if (estaVersion != versionCalculo || isCancelled()) return;
                try {
                    mostrarResultadoCalculo(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (CancellationException ex) {
                } catch (ExecutionException ex) {
                    Throwable causa = ex.getCause();
                    mostrarErrorCalculo(causa == null ? "No se pudo completar el cálculo." : causa.getMessage());
                }
            }
        };
        trabajadorCalculo.execute(); // Disparo el hilo de trabajo
    }

    // Coloca los resultados calculados en las tarjetas KPI, actualiza la gráfica y llena los pasos
    private void mostrarResultadoCalculo(ResultadoCalculo datos) {
        HistorialPasos hist = datos.historial();
        TipoOperacion modo = datos.modo();
        Double a = datos.limiteA(), b = datos.limiteB();
        String v = datos.variable();
        String resultadoOriginal = hist.getExpresionResultado();
        
        if (modo == TipoOperacion.INTEGRAL_DEFINIDA && resultadoOriginal.startsWith("Resultado aproximado")) {
            String valor = obtenerValorIntegral(resultadoOriginal);
            resultadoOriginal = "∫_" + mostrarLimite(datos.limiteA()) + "^" + mostrarLimite(datos.limiteB())
                    + " (" + datos.funcionOriginal() + ") d" + v + " ≈ " + valor.replaceFirst("^≈\\s*", "");
        }
        
        String resultadoLegible = prepararResultadoPrincipal(resultadoOriginal, esCocienteDidactico(datos.funcionOriginal(), v));
        lblKpiResultado.setFont(new Font("Cambria Math", Font.PLAIN, resultadoLegible.length() > 110 ? 20 : resultadoLegible.length() > 70 ? 22 : 26));
        
        int ancho = 520;
        Container padre = lblKpiResultado.getParent();
        if (padre != null && padre.getWidth() > 300) ancho = Math.max(330, Math.min(610, padre.getWidth() - 42));
        lblKpiResultado.setText("<html><div style='width:" + ancho + "px; white-space:normal;'>"
                + escaparHtml(resultadoLegible).replace("\n", "<br>") + "</div></html>");
        lblKpiResultado.setVerticalAlignment(SwingConstants.CENTER);
        ajustarAlturaTarjetaResultado();

        if (modo == TipoOperacion.INTEGRAL_DEFINIDA) {
            lblKpiIntegral.setText(obtenerValorIntegral(hist.getExpresionResultado()));
            lblKpiIntegral.setForeground(TEXTO_BLANCO);
        } else {
            lblKpiIntegral.setText("—");
            lblKpiIntegral.setForeground(TEXTO_BLANCO);
        }
        
        double pendiente = hist.getPendienteEnUno();
        if (modo != TipoOperacion.DERIVADA) {
            lblKpiPendiente.setText("—");
        } else if (Double.isFinite(pendiente)) {
            lblKpiPendiente.setText(TerminoPolinomio.formatearNumero(pendiente));
        } else {
            lblKpiPendiente.setText("No definida en 1");
        }
        
        lblKpiPasos.setText(hist.getPasos().size() + " pasos");
        lblEstado.setForeground(VERDE_NEON);
        lblEstado.setText("● Cálculo completado (respecto de " + v + ")");

        boolean limitesFinitos = modo == TipoOperacion.INTEGRAL_DEFINIDA
                && a != null && b != null && Double.isFinite(a) && Double.isFinite(b);
        graficador.actualizarGrafica(datos.funcion(), v,
                limitesFinitos ? a : null, limitesFinitos ? b : null, modo == TipoOperacion.DERIVADA);
        mostrarPasosEducativos(hist);
    }

    private void ajustarAlturaTarjetaResultado() {
        Container tarjeta = lblKpiResultado.getParent();
        if (tarjeta == null) return;
        int alturaContenido = lblKpiResultado.getPreferredSize().height;
        int alto = Math.max(122, Math.min(300, alturaContenido + 48));
        int ancho = Math.max(350, tarjeta.getWidth() > 350 ? tarjeta.getWidth() : 600);
        tarjeta.setPreferredSize(new Dimension(ancho, alto));
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
        tarjeta.revalidate();
        if (tarjeta.getParent() != null) tarjeta.getParent().revalidate();
        revalidate();
    }

    private String mostrarLimite(Double limite) {
        if (limite == null) return "?";
        if (limite == Double.POSITIVE_INFINITY) return "∞";
        if (limite == Double.NEGATIVE_INFINITY) return "−∞";
        return TerminoPolinomio.formatearNumero(limite);
    }

    private String obtenerValorIntegral(String resultado) {
        if (resultado == null || resultado.isBlank()) return "—";
        int aproximado = resultado.lastIndexOf("≈");
        if (aproximado >= 0) return "≈ " + resultado.substring(aproximado + 1).trim();
        int igualdad = resultado.lastIndexOf(" = ");
        if (igualdad >= 0) return resultado.substring(igualdad + 3).trim();
        return resultado;
    }

    private void mostrarErrorCalculo(String mensaje) {
        lblKpiResultado.setText("—");
        lblKpiIntegral.setText("—");
        lblKpiPendiente.setText("—");
        lblKpiPasos.setText("—");
        lblEstado.setForeground(new Color(239, 68, 68));
        lblEstado.setText("⚠ " + (mensaje == null || mensaje.isBlank() ? "No se pudo completar el cálculo." : mensaje));
        contenedorPasos.removeAll();
        JTextArea error = crearTextoAjustable(mensaje == null ? "Error de cálculo." : mensaje,
                F_CONSOLA, new Color(239, 68, 68), BG_PANEL);
        contenedorPasos.add(error);
        contenedorPasos.revalidate();
        contenedorPasos.repaint();
        graficador.actualizarGrafica(x -> Double.NaN, obtenerVariable(), null, null, false);
    }

    private String prepararResultadoPrincipal(String resultado, boolean cocienteDidactico) {
        String visual = traducirAFormatoMatematico(resultado);
        if (!cocienteDidactico) return visual;
        int igualdad = visual.indexOf(" = ");
        int division = visual.indexOf(" ÷ ", igualdad < 0 ? 0 : igualdad + 3);
        if (igualdad < 0 || division < 0) return visual;
        String encabezado = visual.substring(0, igualdad + 3).trim();
        String numerador = visual.substring(igualdad + 3, division).trim();
        String denominador = visual.substring(division + 3).trim();
        if (numerador.isEmpty() || denominador.isEmpty()) return visual;
        int largoBarra = Math.min(27, Math.max(10, Math.max(numerador.length(), denominador.length())));
        return encabezado + "\n" + numerador + "\n" + "─".repeat(largoBarra) + "\n" + denominador;
    }

    private boolean esCocienteDidactico(String ecuacion, String variable) {
        try {
            String s = resolvedor.normalizarTexto(ecuacion).replace(" ", "").replace("*", "");
            return s.equals("(" + variable + "^2+1)^3/(" + variable + "^2-1)");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // Dibuja cada paso explicativo dentro del panel derecho asegurando que el texto se ajuste al ancho
    private void mostrarPasosEducativos(HistorialPasos hist) {
        contenedorPasos.removeAll();
        contenedorPasos.setBorder(new EmptyBorder(2, 2, 2, 2));
        for (PasoExplicado p : hist.getPasos()) {
            JPanel fila = new JPanel();
            fila.setLayout(new BoxLayout(fila, BoxLayout.Y_AXIS));
            fila.setBackground(BG_PANEL);
            fila.setBorder(new EmptyBorder(12, 12, 14, 12));
            fila.setAlignmentX(Component.LEFT_ALIGNMENT);
            fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            String tituloLimpio = p.reglaAplicada().replaceFirst("^\\s*\\d+[.)]\\s*", "");
            JTextArea titulo = crearTextoAjustable("Paso " + p.numero() + ". " + tituloLimpio,
                    new Font("Segoe UI", Font.BOLD, 16), TEXTO_BLANCO, BG_PANEL);
            fila.add(titulo);
            fila.add(Box.createVerticalStrut(5));

            // Imprimo la explicación detallada que envía el ResolvedorCalculo
            String textoTeorema = (p.teorema() == null || p.teorema().isBlank())
                    ? explicacionDidactica(p)
                    : traducirAFormatoMatematico(p.teorema());
            JTextArea explicacion = crearTextoAjustable(textoTeorema,
                    new Font("Segoe UI", Font.PLAIN, 14), new Color(0xC9C9C9), BG_PANEL);
            fila.add(explicacion);
            fila.add(Box.createVerticalStrut(9));

            String operacionVisual = traducirAFormatoMatematico(p.operacion());
            JTextArea operacion = crearTextoAjustable(operacionVisual,
                    new Font("Cambria Math", Font.PLAIN, 20), VERDE_NEON, BG_APP);
            operacion.setBorder(new EmptyBorder(9, 10, 9, 10));
            fila.add(operacion);

            JSeparator separador = new JSeparator(SwingConstants.HORIZONTAL);
            separador.setForeground(new Color(0x353535));
            separador.setBackground(new Color(0x353535));
            fila.add(Box.createVerticalStrut(14));
            fila.add(separador);
            contenedorPasos.add(fila);
        }
        contenedorPasos.revalidate();
        contenedorPasos.repaint();
    }

    private String explicacionDidactica(PasoExplicado p) {
        String original = p.teorema() == null ? "" : p.teorema();
        if (!original.isBlank()) return original;
        return "En este paso se transforma la expresión anterior para acercarnos al resultado.";
    }

    // Crea un bloque de texto que salta de renglón automáticamente (LineWrap) para no salirse nunca de la pantalla
    private JTextArea crearTextoAjustable(String texto, Font fuente, Color color, Color fondo) {
        String contenido = texto == null ? "" : texto;
        JTextArea area = new JTextArea(contenido);
        area.setColumns(34);
        area.setRows(Math.max(1, Math.min(14, contarLineasEstimadas(contenido, 42))));
        area.setFont(fuente);
        area.setForeground(color);
        area.setBackground(fondo);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true); // Fuerza el salto de línea al llegar al borde derecho
        area.setWrapStyleWord(true); // Corta por palabras completas y no a mitad de palabra
        area.setOpaque(true);
        area.setBorder(new EmptyBorder(2, 0, 2, 0));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }

    private int contarLineasEstimadas(String texto, int ancho) {
        int lineas = 0;
        for (String linea : texto.split("\\R", -1)) {
            lineas += Math.max(1, (int) Math.ceil(linea.length() / (double) ancho));
        }
        return lineas;
    }

    private String escaparHtml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void exportarGrafica() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Gráfica");
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen JPG", "jpg"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith(".jpg")) {
                archivo = new File(archivo.getParentFile(), archivo.getName() + ".jpg");
            }
            try {
                graficador.exportarJPG(archivo);
                JOptionPane.showMessageDialog(this, "Gráfica guardada correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error al exportar: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Muestra la Guía de Usuario con el ancho corregido (680x480 y tabla de 480px) para que no se corte ni una letra
    private void mostrarManualUsuario() {
        JDialog modal = new JDialog(this, "Guía de Usuario", true); 
        modal.setUndecorated(true); // Adiós marco de Windows
        modal.setSize(680, 480); 
        modal.setLocationRelativeTo(this); // Que salga en el puro centro
        
        JPanel panelPrincipal = new JPanel(new BorderLayout(0, 15));
        panelPrincipal.setBackground(BG_PANEL); 
        panelPrincipal.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(VERDE_NEON, 2), new EmptyBorder(24, 28, 24, 28))); 
        
        JLabel titulo = new JLabel("∫ GUÍA DE USUARIO", SwingConstants.CENTER);
        titulo.setFont(new Font("Consolas", Font.BOLD, 28));
        titulo.setForeground(VERDE_NEON);
        
        // HTML ajustado: 'align=center' en la tabla y ancho de 500px para centrar el bloque de texto
        String contenidoHtml = "<html><div style='font-family: Consolas; font-size: 13px; color: #F8F8F8; text-align: center; width: 100%;'>" +
            "<table align='center' cellpadding='8' cellspacing='0' width='500' style='text-align: left;'>" +
            "<tr><td valign='top' width='36'><span style='font-size: 20px;'>⌨️</span></td>" +
            "<td><b>Escritura y Variables (x, y):</b> Escribe funciones simples o con ambas variables (ej. 2x + 4y) y elige respecto a cuál operar.</td></tr>" +
            "<tr><td valign='top' width='36'><span style='font-size: 20px;'>⚙️</span></td>" +
            "<td><b>Límites Inteligentes:</b> Las cajas de límites [a, b] aparecen solo cuando seleccionas <i>Integral Definida</i>.</td></tr>" +
            "<tr><td valign='top' width='36'><span style='font-size: 20px;'>♾️</span></td>" +
            "<td><b>Integrales Impropias:</b> Haz clic en 'a:' o 'b:' y usa el botón <b>∞</b>. El motor detectará si converge o diverge.</td></tr>" +
            "<tr><td valign='top' width='36'><span style='font-size: 20px;'>📈</span></td>" +
            "<td><b>Graficador Táctico:</b> Rueda del mouse para zoom, arrastrar para mover, y <b>doble clic</b> para reiniciar la vista.</td></tr>" +
            "</table>" +
            "</div></html>";
            
        JLabel lblTexto = new JLabel(contenidoHtml, SwingConstants.CENTER);
        
        // Envuelvo el texto en un GridBagLayout transparente para forzar el centrado absoluto en todos los ejes
        JPanel panelCentro = new JPanel(new GridBagLayout());
        panelCentro.setOpaque(false);
        panelCentro.add(lblTexto);
        
        JButton btnCerrar = new JButton("Comenzar a Calcular"); 
        btnCerrar.setFont(new Font("Consolas", Font.BOLD, 15));
        btnCerrar.setBackground(VERDE_OSCURO); 
        btnCerrar.setForeground(TEXTO_BLANCO);
        btnCerrar.setFocusPainted(false);
        btnCerrar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCerrar.setBorder(new EmptyBorder(10, 24, 10, 24));
        btnCerrar.addActionListener(e -> modal.dispose()); 
        
        JPanel pnlBoton = new JPanel(); 
        pnlBoton.setOpaque(false); 
        pnlBoton.add(btnCerrar);
        
        panelPrincipal.add(titulo, BorderLayout.NORTH);
        panelPrincipal.add(panelCentro, BorderLayout.CENTER); // Ahora agrego el panel centrado en lugar del JLabel directo
        panelPrincipal.add(pnlBoton, BorderLayout.SOUTH);
        
        modal.add(panelPrincipal);
        modal.setVisible(true); 
    }
    private JPanel crearTarjetaBase() {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BG_APP, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        return p;
    }

    private JPanel tarjetaResultadoPrincipal() {
        JPanel t = crearTarjetaBase();
        t.setLayout(new BorderLayout(0, 8));
        JLabel titulo = new JLabel("RESULTADO PRINCIPAL");
        titulo.setFont(new Font("Consolas", Font.BOLD, 11));
        titulo.setForeground(Color.GRAY);
        lblKpiResultado.setFont(new Font("Cambria Math", Font.PLAIN, 26));
        lblKpiResultado.setForeground(VERDE_NEON);
        lblKpiResultado.setHorizontalAlignment(SwingConstants.LEFT);
        lblKpiResultado.setVerticalAlignment(SwingConstants.CENTER);
        t.add(titulo, BorderLayout.NORTH);
        t.add(lblKpiResultado, BorderLayout.CENTER);
        t.setPreferredSize(new Dimension(600, 122));
        t.setMinimumSize(new Dimension(350, 110));
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        return t;
    }

    private JPanel tarjetaIndicador(String titulo, JLabel valor, Color colorTexto) {
        JPanel t = crearTarjetaBase();
        t.setLayout(new BorderLayout(0, 6));
        JLabel lblTit = new JLabel(titulo);
        lblTit.setFont(new Font("Consolas", Font.BOLD, 11));
        lblTit.setForeground(Color.GRAY);
        valor.setFont(new Font("Cambria Math", Font.BOLD, 19));
        valor.setForeground(colorTexto);
        valor.setHorizontalAlignment(SwingConstants.LEFT);
        valor.setVerticalAlignment(SwingConstants.CENTER);
        t.add(lblTit, BorderLayout.NORTH);
        t.add(valor, BorderLayout.CENTER);
        t.setPreferredSize(new Dimension(240, 82));
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        return t;
    }

    private void estilizarCampoLimite(JTextField c) {
        c.setColumns(5);
        c.setPreferredSize(new Dimension(58, 30));
        c.setBackground(BG_APP);
        c.setForeground(TEXTO_BLANCO);
        c.setFont(F_CONSOLA);
        c.setCaretColor(VERDE_NEON);
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x333333), 1), new EmptyBorder(4, 6, 4, 6)));
    }

    private JLabel etiqueta(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Color.GRAY);
        l.setFont(F_CONSOLA);
        return l;
    }

    // Panel que obliga al ScrollPane a ajustar todo el contenido al ancho disponible sin desbordarse
    private static class ScrollablePanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 50; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; } 
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}