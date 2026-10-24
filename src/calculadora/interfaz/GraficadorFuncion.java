package calculadora.interfaz; // Paquete de la interfaz gráfica

import javax.imageio.ImageIO; // Para guardar la imagen en archivo JPG
import javax.swing.JPanel; // Clase base del lienzo donde dibujamos
import javax.swing.Timer; // Temporizador para animar la curva a 60 cuadros por segundo
import java.awt.*; // Herramientas de dibujo 2D, colores, trazos y fuentes
import java.awt.event.MouseAdapter; // Adaptador para escuchar clics y arrastres del mouse
import java.awt.event.MouseEvent; // Datos de la posición del puntero del mouse
import java.awt.geom.Ellipse2D; // Para dibujar el punto blanco que sigue la curva
import java.awt.geom.Path2D; // Para construir la ruta continua de la curva y el área sombreada
import java.awt.geom.RoundRectangle2D; // Para darle bordes redondeados al fondo del gráfico
import java.awt.image.BufferedImage; // Imagen en memoria RAM para exportar el JPG
import java.io.File; // Representa el archivo físico donde se guarda el JPG
import java.io.IOException; // Excepción por si falla la escritura en disco
import java.util.function.DoubleUnaryOperator; // Función matemática evaluable f(x)

public class GraficadorFuncion extends JPanel { // Panel personalizado que dibuja el plano cartesiano

    private static final int MUESTRAS = 240; // Cantidad de puntos que calculamos de izquierda a derecha
    private final double[] yActual = new double[MUESTRAS]; // Posición Y actual en pantalla (para la animación)
    private final double[] yObjetivo = new double[MUESTRAS]; // Posición Y real a la que debe llegar la curva
    private final double[] yDerivada = new double[MUESTRAS]; // Valores Y de la derivada f'(x)

    private DoubleUnaryOperator funcion = x -> 0; // Función activa que estamos graficando
    private double xMin = -6.0, xMax = 6.0, yMin = -6.0, yMax = 10.0; // Rango inicial de los ejes X y Y
    private Double limA = null, limB = null; // Límites [a, b] para sombrear el área de la integral definida
    private boolean mostrarDerivada = true; // Indica si debemos trazar también la línea punteada de f'(x)
    private String variable = "x"; // Nombre de la variable del eje horizontal

    private Point posMouse = null, ultimoArrastre = null; // Coordenadas del mouse para la mira y el arrastre
    
    // Timer que se ejecuta cada 16 milisegundos para mover la curva suavemente hacia la nueva función
    private final Timer animador = new Timer(16, e -> {
        boolean enMovimiento = false; // Bandera para saber si algún punto sigue moviéndose
        for (int i = 0; i < MUESTRAS; i++) { // Recorro los 240 puntos de la curva
            if (Double.isFinite(yObjetivo[i])) { // Si el punto destino existe
                if (!Double.isFinite(yActual[i])) {
                    yActual[i] = yObjetivo[i]; // Si antes no existía, salta directo al valor
                } else {
                    double dif = yObjetivo[i] - yActual[i]; // Calculo qué tan lejos está de su destino
                    if (Math.abs(dif) > 0.004) { // Si todavía está lejos
                        yActual[i] += dif * 0.22; // Avanzo un 22% de la distancia en cada frame
                        enMovimiento = true; // Marco que la animación debe continuar
                    } else {
                        yActual[i] = yObjetivo[i]; // Si ya está muy cerca, lo fijo en el destino
                    }
                }
            } else {
                yActual[i] = Double.NaN; // Si el punto es indefinido, no lo dibujo
            }
        }
        repaint(); // Fuerzo a Swing a redibujar el panel con las nuevas posiciones
        if (!enMovimiento) { // Si todos los puntos llegaron a su lugar
            ((Timer)e.getSource()).stop(); // Detengo el timer para no gastar CPU en vano
        }
    });

    // Colores del tema oscuro de la gráfica
    private static final Color BG_APP = new Color(0x0F0F0F); // Negro profundo de fondo
    private static final Color BG_PANEL = new Color(0x202020); // Gris oscuro para las líneas de la cuadrícula
    private static final Color VERDE_NEON = new Color(0x5DD62C); // Verde brillante para la curva principal
    private static final Color VERDE_OSCURO = new Color(0x337418); // Verde oscuro para los ejes centrales
    private static final Color TEXTO_BLANCO = new Color(0xF8F8F8); // Blanco para las coordenadas del mouse

    public GraficadorFuncion() { // Constructor del graficador
        setOpaque(false); // Lo hago transparente para dibujar yo mismo el rectángulo redondeado
        configurarMouse(); // Activo los eventos de zoom, arrastre y doble clic
        actualizarGrafica(x -> Double.NaN, "x", null, null, false); // Inicio el plano limpio sin curva
    }

    // Configura toda la interactividad del usuario con el mouse sobre el plano
    private void configurarMouse() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { ultimoArrastre = e.getPoint(); } // Guardo dónde empezó a arrastrar
            @Override public void mouseReleased(MouseEvent e) { ultimoArrastre = null; } // Suelto el arrastre al soltar el clic
            @Override public void mouseDragged(MouseEvent e) { // Cuando mueve el mouse con el clic presionado
                if (ultimoArrastre != null) {
                    double dx = (e.getX() - ultimoArrastre.x) * (xMax - xMin) / Math.max(1, getWidth()); // Paso píxeles X a unidades del plano
                    double dy = (e.getY() - ultimoArrastre.y) * (yMax - yMin) / Math.max(1, getHeight()); // Paso píxeles Y a unidades del plano
                    xMin -= dx; xMax -= dx; yMin += dy; yMax += dy; // Desplazo los límites del plano cartesiano
                    ultimoArrastre = e.getPoint(); // Actualizo el último punto de arrastre
                    recalcularInstantaneo(); // Recalculo la curva en el nuevo rango visible
                    repaint(); // Redibujo de inmediato
                }
            }
            @Override public void mouseMoved(MouseEvent e) { posMouse = e.getPoint(); repaint(); } // Actualizo la mira al mover el mouse
            @Override public void mouseExited(MouseEvent e) { posMouse = null; repaint(); } // Oculto la mira cuando el mouse sale del gráfico
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { // Zoom con la rueda del ratón
                double f = e.getPreciseWheelRotation() > 0 ? 1.15 : 0.85; // Si gira hacia atrás aleja (1.15), hacia adelante acerca (0.85)
                double amplitudX = (xMax - xMin) * f; // Calculo el nuevo ancho del eje X
                if (amplitudX > 100000.0 || amplitudX < 0.1) return; // Límite de seguridad para que no se trabe por zoom infinito

                double cx = (xMin + xMax) / 2.0, cy = (yMin + yMax) / 2.0; // Centro actual de la pantalla
                double mx = (xMax - xMin) / 2.0 * f, my = (yMax - yMin) / 2.0 * f; // Nuevas mitades escaladas
                xMin = cx - mx; xMax = cx + mx; yMin = cy - my; yMax = cy + my; // Aplico el nuevo zoom centrado
                recalcularInstantaneo(); // Evalúo la función en la nueva escala
                repaint(); // Pinto el nuevo zoom
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Si el usuario da doble clic
                    xMin = -6.0; xMax = 6.0; yMin = -6.0; yMax = 10.0; // Restauro la vista original centrada
                    recalcularInstantaneo();
                    repaint();
                }
            }
        };
        addMouseListener(ma); // Conecto los eventos de clic
        addMouseMotionListener(ma); // Conecto los eventos de movimiento y arrastre
        addMouseWheelListener(ma); // Conecto el evento de la rueda del mouse
    }

    // Actualiza los datos de la función y lanza la animación suave
    public void actualizarGrafica(DoubleUnaryOperator fn, String var, Double a, Double b, boolean verDerivada) {
        this.funcion = fn; // Guardo la nueva función matemática
        this.variable = var; // Guardo la letra de la variable activa ("x" o "y")
        this.limA = a; // Guardo el límite inferior si es integral definida
        this.limB = b; // Guardo el límite superior si es integral definida
        this.mostrarDerivada = verDerivada; // Guardo si hay que mostrar la curva derivada gris

        for (int i = 0; i < MUESTRAS; i++) { // Muestreo los 240 puntos a lo largo del eje X
            double x = xMin + (xMax - xMin) * i / (MUESTRAS - 1); // Coordenada X de este punto
            double y = fn.applyAsDouble(x); // Evalúo cuánto vale f(x)
            yObjetivo[i] = (Double.isFinite(y)) ? y : Double.NaN; // Guardo el valor objetivo
            double h = 1e-4; // Incremento pequeño para estimar la derivada
            double dy = (fn.applyAsDouble(x + h) - fn.applyAsDouble(x - h)) / (2 * h); // Pendiente en este punto
            yDerivada[i] = (Double.isFinite(dy)) ? dy : Double.NaN; // Guardo el punto de la derivada
        }
        if (!animador.isRunning()) animador.start(); // Arranco el timer de animación si estaba detenido
    }

    // Recalcula sin animación (se usa al arrastrar o hacer zoom para que responda al instante)
    private void recalcularInstantaneo() {
        for (int i = 0; i < MUESTRAS; i++) {
            double x = xMin + (xMax - xMin) * i / (MUESTRAS - 1);
            double y = funcion.applyAsDouble(x);
            yObjetivo[i] = yActual[i] = (Double.isFinite(y)) ? y : Double.NaN; // Igualo actual y objetivo de una vez
            double h = 1e-4;
            yDerivada[i] = (funcion.applyAsDouble(x + h) - funcion.applyAsDouble(x - h)) / (2 * h);
        }
    }

    // Calcula de cuánto en cuánto van los números del plano (1, 2, 5, 10, 50...) según el zoom
    private double calcularPasoEscala(double rango) {
        double magnitud = Math.pow(10, Math.floor(Math.log10(rango / 5.0))); // Potencia de 10 base según el tamaño de la vista
        double fraccion = (rango / 5.0) / magnitud; // Veo cuántas divisiones caben
        if (fraccion < 1.5) return magnitud; // Escala de 1 en 1 (o 10 en 10, etc.)
        if (fraccion < 3.5) return magnitud * 2.0; // Escala de 2 en 2 (o 20 en 20)
        if (fraccion < 7.5) return magnitud * 5.0; // Escala de 5 en 5 (o 50 en 50)
        return magnitud * 10.0; // Escala siguiente potencia de 10
    }

    private double px(double x) { return (x - xMin) / (xMax - xMin) * getWidth(); } // Convierte coordenada X a píxel horizontal
    
    private double py(double y) { // Convierte coordenada Y a píxel vertical (invirtiendo porque en pantallas Y crece hacia abajo)
        double pos = getHeight() - (y - yMin) / (yMax - yMin) * getHeight(); 
        return Math.max(-10000, Math.min(pos, 10000)); // Limito entre -10000 y 10000 para que Java2D nunca se cuelgue
    }
    
    private double mundoX(double p) { return xMin + (p / getWidth()) * (xMax - xMin); } // Convierte un píxel del mouse a coordenada X real

    // Exporta el gráfico actual exactamente con el zoom que tenga puesto el usuario a un archivo JPG
    public void exportarJPG(File archivo) throws IOException {
        BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB); // Creo el lienzo de imagen
        Graphics2D g2 = img.createGraphics(); // Obtengo el pincel gráfico de la imagen
        paintComponent(g2); // Llamo a mi propio método de dibujo sobre la imagen
        g2.dispose(); // Libero recursos de memoria gráfica
        ImageIO.write(img, "jpg", archivo); // Escribo el archivo físico en formato JPG
    }

    @Override
    protected void paintComponent(Graphics g) { // Método de Swing que dibuja todo el componente en pantalla
        Graphics2D g2 = (Graphics2D) g.create(); // Creo una copia segura del pincel Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Suavizo las curvas
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // Suavizo los números

        int w = getWidth(), h = getHeight(); // Ancho y alto actual del panel
        g2.setColor(BG_APP); // Pongo color negro profundo
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 18, 18)); // Pinto el fondo redondeado

        dibujarPlano(g2, w, h); // 1. Dibujo la cuadrícula y los ejes X, Y
        if (limA != null && limB != null) dibujarSombreado(g2, h); // 2. Si es integral definida, pinto el área bajo la curva
        
        if (mostrarDerivada) { // 3. Si estamos en modo derivada, dibujo la línea gris punteada de f'(x)
            dibujarTrazo(g2, yDerivada, Color.GRAY, new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 6f}, 0f), false);
        }
        
        // 4. Dibujo la función principal f(x) en verde neón con efecto de resplandor activado (true)
        dibujarTrazo(g2, yActual, VERDE_NEON, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND), true);
        dibujarMiraMouse(g2, w, h); // 5. Dibujo el punto interactivo si el usuario tiene el mouse encima

        g2.setColor(BG_PANEL); // Color del borde exterior de la tarjeta gráfica
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 18, 18)); // Dibujo el marco sutil
        g2.dispose(); // Cierro el pincel gráfico
    }

    // Dibuja las líneas verticales, horizontales, los números de escala y los ejes principales
    private void dibujarPlano(Graphics2D g2, int w, int h) {
        g2.setColor(BG_PANEL);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        
        double pasoX = calcularPasoEscala(xMax - xMin); // Salto dinámico en el eje X
        double pasoY = calcularPasoEscala(yMax - yMin); // Salto dinámico en el eje Y

        for (double x = Math.ceil(xMin / pasoX) * pasoX; x <= xMax; x += pasoX) { // Recorro las marcas verticales
            int p = (int) px(x); // Posición en píxeles de esta línea vertical
            g2.drawLine(p, 0, p, h); // Trazo la línea de arriba a abajo
            if (Math.abs(x) > 1e-9) { // Evito encimar el número 0 en el cruce de los ejes
                g2.setColor(Color.GRAY);
                String label = (x == Math.floor(x)) ? String.valueOf((long)x) : String.format("%.1f", x); // Formato del número
                g2.drawString(label, p - 4, Math.min(h - 8, Math.max(15, (int) py(0) + 16))); // Escribo el número junto al eje
                g2.setColor(BG_PANEL);
            }
        }
        
        for (double y = Math.ceil(yMin / pasoY) * pasoY; y <= yMax; y += pasoY) { // Recorro las marcas horizontales
            int p = (int) py(y); // Posición en píxeles de esta línea horizontal
            g2.drawLine(0, p, w, p); // Trazo la línea de izquierda a derecha
            if (Math.abs(y) > 1e-9) {
                g2.setColor(Color.GRAY);
                String label = (y == Math.floor(y)) ? String.valueOf((long)y) : String.format("%.1f", y);
                g2.drawString(label, Math.min(w - 25, Math.max(6, (int) px(0) + 6)), p + 4); // Escribo el número junto al eje Y
                g2.setColor(BG_PANEL);
            }
        }
        
        g2.setColor(VERDE_OSCURO); // Color para los ejes centrales (x = 0, y = 0)
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(0, (int) py(0), w, (int) py(0)); // Dibujo el eje horizontal X
        g2.drawLine((int) px(0), 0, (int) px(0), h); // Dibujo el eje vertical Y
    }

    // Pinta el polígono degradado bajo la curva entre el límite 'a' y el límite 'b'
    private void dibujarSombreado(Graphics2D g2, int h) {
        double a = Math.min(limA, limB), b = Math.max(limA, limB); // Ordeno de menor a mayor para sombrear
        Path2D area = new Path2D.Double(); // Creo una figura cerrada Path2D
        double base = py(0); // Altura del eje X (y = 0)
        area.moveTo(px(a), base); // Empiezo en (a, 0)
        for (int i = 0; i <= 100; i++) { // Recorro 100 puntos siguiendo la silueta de la función
            double x = a + (b - a) * i / 100.0;
            double y = funcion.applyAsDouble(x);
            if (Double.isFinite(y)) area.lineTo(px(x), py(y)); // Uno el borde superior con la curva
        }
        area.lineTo(px(b), base); // Bajo hacia (b, 0)
        area.closePath(); // Cierro la figura geométrica
        
        g2.setPaint(new GradientPaint(0, 0, new Color(93, 214, 44, 90), 0, h, new Color(93, 214, 44, 10))); // Degradado verde translúcido
        g2.fill(area); // Relleno el área bajo la curva
    }

    // Une todos los puntos muestreados para dibujar una curva continua
    private void dibujarTrazo(Graphics2D g2, double[] datos, Color color, BasicStroke stroke, boolean brillar) {
        Path2D path = new Path2D.Double();
        boolean activo = false;
        for (int i = 0; i < MUESTRAS; i++) {
            double y = datos[i];
            if (!Double.isFinite(y)) { activo = false; continue; } // Si hay una discontinuidad, corto el trazo y lo retomo después
            double x = xMin + (xMax - xMin) * i / (MUESTRAS - 1);
            if (!activo) { path.moveTo(px(x), py(y)); activo = true; } // Primer punto válido del segmento
            else path.lineTo(px(x), py(y)); // Conecto con el punto siguiente
        }
        if (brillar) { // Si tiene activado el efecto neón
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60)); // Mismo color pero semitransparente
            g2.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); // Trazo grueso de fondo
            g2.draw(path); // Dibujo el aura brillante
        }
        g2.setColor(color); // Pongo el color sólido principal
        g2.setStroke(stroke); // Pongo el grosor normal
        g2.draw(path); // Dibujo la línea nítida encima
    }

    // Muestra el círculo sobre la curva y la etiqueta flotante con las coordenadas exactas donde está el mouse
    private void dibujarMiraMouse(Graphics2D g2, int w, int h) {
        if (posMouse == null) return; // Si el mouse no está dentro del gráfico, no dibujo nada
        double x = mundoX(posMouse.x); // Calculo qué valor de X corresponde al píxel del mouse
        double y = funcion.applyAsDouble(x); // Evalúo cuánto vale la función en ese punto X
        if (!Double.isFinite(y)) return; // Si la función no existe ahí, no muestro la mira

        int pY = (int) py(y); // Píxel vertical exacto sobre la curva
        g2.setColor(new Color(248, 248, 248, 70)); 
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0f));
        g2.drawLine(posMouse.x, pY, posMouse.x, h); // Línea guía punteada vertical
        g2.setColor(TEXTO_BLANCO);
        g2.fill(new Ellipse2D.Double(posMouse.x - 4, pY - 4, 8, 8)); // Puntito blanco sobre la curva

        String info = String.format(java.util.Locale.US, "%s = %.2f  |  f(%s) = %.2f", variable, x, variable, y); // Texto del cartelito
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        int bw = g2.getFontMetrics().stringWidth(info) + 18; // Calculo el ancho de la etiqueta según el texto
        int bx = Math.min(w - bw - 10, Math.max(10, posMouse.x - bw / 2)); // Evito que la etiqueta se salga por los lados
        int by = Math.max(12, pY - 34); // La coloco arribita del punto

        g2.setColor(BG_APP);
        g2.fillRoundRect(bx, by, bw, 24, 8, 8); // Fondo negro de la etiqueta flotante
        g2.setColor(VERDE_NEON);
        g2.drawRoundRect(bx, by, bw, 24, 8, 8); // Borde verde neón de la etiqueta
        g2.setColor(TEXTO_BLANCO);
        g2.drawString(info, bx + 9, by + 16); // Escribo los valores de x y f(x)
    }
}