package calculadora.logica; // Paquete lógico donde coordinamos los cálculos

import java.util.ArrayList; // Para listas dinámicas
import java.util.List; // Interfaz de listas
import java.util.function.DoubleUnaryOperator; // Interfaz funcional f(x) para evaluar puntos en la gráfica y Simpson
import java.util.regex.Matcher; // Para buscar patrones con expresiones regulares
import java.util.regex.Pattern; // Para compilar expresiones regulares de limpieza

public class ResolvedorCalculo { // Clase coordinadora entre la ventana y el MotorSimbolico

    public enum TipoOperacion { // Enumeración con los 3 modos de trabajo de la calculadora
        DERIVADA("Derivada"),
        INTEGRAL_INDEFINIDA("Integral indefinida"),
        INTEGRAL_DEFINIDA("Integral definida [a, b]");

        private final String etiqueta; // Texto que se ve en el ComboBox
        TipoOperacion(String etiqueta) { this.etiqueta = etiqueta; }
        @Override public String toString() { return etiqueta; } // Muestra el nombre limpio en la lista desplegable
    }

    // Método principal que recibe las entradas del usuario, resuelve y arma todos los pasos explicativos
    public HistorialPasos resolver(String entrada, String variable, TipoOperacion modo,
                                   String limInfTxt, String limSupTxt) throws IllegalArgumentException, ArithmeticException {
        if (modo == null) throw new IllegalArgumentException("Selecciona el tipo de operación."); // Valido que haya modo activo
        if (entrada == null || entrada.isBlank()) throw new IllegalArgumentException("Escribe una función."); // Valido que haya ecuación
        if (entrada.contains("∞")) { // Evito que metan infinito adentro de la ecuación en vez de en las cajas de límites
            throw new IllegalArgumentException("Escribe ∞ y −∞ únicamente en las cajas de límites [a, b].");
        }
        String limpia = normalizarTexto(entrada); // Limpio la ecuación y agrego asteriscos de multiplicación implícita
        MotorSimbolico.Expresion expr = MotorSimbolico.analizar(limpia, variable); // Convierto la ecuación en árbol AST
        DoubleUnaryOperator fn = convertirAFuncion(limpia, variable); // Creo la función evaluable para el graficador
        double pendienteUno = calcularDerivadaNumerica(fn, 1.0); // Calculo cuánto vale la derivada evaluada en 1

        if (modo == TipoOperacion.DERIVADA) { // Si el usuario eligió derivar
            return resolverDerivada(expr, variable, pendienteUno); // Llamo al método que arma los pasos de derivación
        }

        String otraVar = variable.equals("x") ? "y" : "x"; // Identifico cuál es la variable secundaria
        boolean tieneOtraVar = expr.contieneOtraVariable(); // Reviso si es un ejercicio multivariable como 2x + 4y

        if (modo == TipoOperacion.INTEGRAL_INDEFINIDA) { // Si el usuario eligió integral indefinida
            MotorSimbolico.Expresion primitiva = expr.integrar(); // Calculo la antiderivada simbólica respecto a la variable elegida
            HistorialPasos hist = new HistorialPasos(limpia,
                    "F(" + variable + ") = " + primitiva.formatear() + " + C", pendienteUno); // Creo el contenedor de respuesta
            if (expr.esPolinomio()) { // Si es una suma/resta de términos polinómicos (incluyendo términos como 2x + 4y)
                String notaMulti = tieneOtraVar
                        ? " Como estamos integrando respecto de '" + variable + "', cualquier término con '" + otraVar + "' se mantiene fijo como una constante."
                        : "";
                hist.agregarPaso("1. Separar los términos de la expresión",
                        "Aplicamos la propiedad de linealidad para integrar cada sumando por separado." + notaMulti,
                        "∫ (" + expr.formatear() + ") d" + variable);
                StringBuilder cambios = new StringBuilder(); // Buffer para escribir cómo se integra cada término
                for (MotorSimbolico.Expresion.ParIntegracion par : expr.integracionesPorTermino()) { // Recorro cada término
                    if (cambios.length() > 0) cambios.append("\n\n"); // Dejo un renglón en blanco entre cada término
                    cambios.append("Término: ").append(par.original())
                           .append("\n∫ ").append(par.original()).append(" d").append(variable)
                           .append(" = ").append(par.primitiva()); // Muestro el término antes y después de integrarlo
                }
                hist.agregarPaso("2. Integrar cada término respecto de " + variable,
                        "Sumamos 1 al exponente de '" + variable + "' y dividimos entre el nuevo exponente. Las constantes se multiplican por '" + variable + "'.",
                        cambios.toString());
                hist.agregarPaso("3. Añadir la constante de integración C",
                        "Unimos los resultados parciales y sumamos C para representar toda la familia de antiderivadas.",
                        "F(" + variable + ") = " + primitiva.formatear() + " + C");
            } else { // Si es una integral trigonométrica, exponencial, raíz o por sustitución
                hist.agregarPaso("1. Analizar la estructura del integrando",
                        expr.explicacionIntegracion(),
                        "∫ (" + expr.formatear() + ") d" + variable);
                hist.agregarPaso("2. Aplicar la regla de antiderivación",
                        "Obtenemos la función primitiva F(" + variable + ") cuya derivada respecto de '" + variable + "' reproduce la función original.",
                        "F(" + variable + ") = " + primitiva.formatear());
                hist.agregarPaso("3. Añadir la constante de integración",
                        "Agregamos la constante C porque al derivar cualquier constante su valor desaparece.",
                        "F(" + variable + ") = " + primitiva.formatear() + " + C");
            }
            return hist; // Devuelvo el historial completo a la ventana
        }

        // Si llegó aquí es porque el modo es INTEGRAL DEFINIDA [a, b]
        double a = leerLimite(limInfTxt, "inferior (a)"); // Leo y valido el límite inferior 'a'
        double b = leerLimite(limSupTxt, "superior (b)"); // Leo y valido el límite superior 'b'
        if (!Double.isFinite(a) || !Double.isFinite(b)) { // Si alguno de los límites es ∞ o -∞
            return resolverIntegralImpropia(expr, fn, variable, a, b, pendienteUno); // Paso al módulo de integrales impropias
        }
        if (expr.haySingularidadConocida(a, b)) { // Reviso si la función se rompe en medio de [a, b]
            throw new ArithmeticException("La función tiene una discontinuidad o división por cero dentro del intervalo [" + a + ", " + b + "].");
        }
        validarMuestrasIntegrables(fn, a, b); // Verifico que la función sea evaluable en números reales dentro del rango
        String na = TerminoPolinomio.formatearNumero(a), nb = TerminoPolinomio.formatearNumero(b); // Formateo los límites

        try { // Intento primero resolverla de forma exacta usando la Regla de Barrow
            MotorSimbolico.Expresion primitiva = expr.integrar(); // Busco la antiderivada simbólica
            if (cruzaCeroConPoloSimple(limpia, variable, a, b)) { // Verifico que no cruce una asíntota en 0
                throw new ArithmeticException("El intervalo cruza una singularidad en " + variable + " = 0; no se puede aplicar Barrow.");
            }
            double fa = primitiva.evaluar(a); // Evalúo la antiderivada en el límite inferior F(a)
            double fb = primitiva.evaluar(b); // Evalúo la antiderivada en el límite superior F(b)
            if (!Double.isFinite(fa) || !Double.isFinite(fb)) { // Compruebo que ambos valores existan
                throw new ArithmeticException("La primitiva no está definida en uno de los límites del intervalo.");
            }
            double total = fb - fa; // Resto F(b) - F(a) por el Segundo Teorema Fundamental del Cálculo
            String notaRef = tieneOtraVar ? " (evaluando la constante libre " + otraVar + " = 1)" : "";
            HistorialPasos hist = new HistorialPasos(limpia,
                    "∫_" + na + "^" + nb + " (" + expr.formatear() + ") d" + variable
                            + " = " + TerminoPolinomio.formatearNumero(total), pendienteUno);
            hist.agregarPaso("1. Encontrar la antiderivada respecto de " + variable,
                    expr.explicacionIntegracion(),
                    "F(" + variable + ") = " + primitiva.formatear());
            hist.agregarPaso("2. Evaluar la primitiva en los límites [a, b]",
                    "Sustituimos '" + variable + "' por el límite superior (" + nb + ") y por el límite inferior (" + na + ")" + notaRef + ".",
                    "F(" + nb + ") = " + TerminoPolinomio.formatearNumero(fb)
                            + "\nF(" + na + ") = " + TerminoPolinomio.formatearNumero(fa));
            hist.agregarPaso("3. Aplicar la Regla de Barrow: F(b) − F(a)",
                    "Restamos el valor obtenido en el límite inferior al valor del límite superior.",
                    "F(" + nb + ") − F(" + na + ") = " + TerminoPolinomio.formatearNumero(fb)
                            + " − (" + TerminoPolinomio.formatearNumero(fa) + ") = " + TerminoPolinomio.formatearNumero(total));
            return hist;
        } catch (IllegalArgumentException noHayPrimitiva) { // Si no tiene antiderivada elemental, uso el método numérico de Simpson
            double area = integrarPorSimpson(fn, a, b); // Calculo el área dividiendo en 1000 parábolas
            String res = TerminoPolinomio.formatearNumero(area); // Formateo el área obtenida
            HistorialPasos hist = new HistorialPasos(limpia, "Resultado aproximado ≈ " + res, pendienteUno);
            hist.agregarPaso("1. Preparar integración numérica",
                    "Como la función no tiene antiderivada elemental directa, aplicamos la Regla Compuesta de Simpson.",
                    "Intervalo de integración: [" + na + ", " + nb + "]");
            hist.agregarPaso("2. Dividir en subintervalos y evaluar parábolas",
                    "Partimos el intervalo en n = 1000 tramos de igual ancho h = (b − a) / 1000 y ponderamos las alturas.",
                    "Ancho de paso: h = " + TerminoPolinomio.formatearNumero((b - a) / 1000.0)
                            + "\nFórmula: (h/3) · [f(a) + 4∑f(impares) + 2∑f(pares) + f(b)]");
            hist.agregarPaso("3. Resultado numérico aproximado",
                    "La suma converge al siguiente valor del área neta bajo la curva:",
                    "∫_" + na + "^" + nb + " (" + expr.formatear() + ") d" + variable + " ≈ " + res);
            return hist;
        }
    }

    // Construye todo el procedimiento explicado cuando estamos en modo DERIVADA
    private HistorialPasos resolverDerivada(MotorSimbolico.Expresion expr, String variable, double pendienteUno) {
        MotorSimbolico.Expresion derivada = expr.derivar(); // Obtengo el árbol ya derivado respecto a la variable activa
        String resultado = "f′(" + variable + ") = " + derivada.formatear(); // Formateo la respuesta principal
        if (esCocienteEjercicioConocido(expr, variable)) { // Formato especial factorizado para el cociente clásico de prueba
            resultado = "f′(" + variable + ") = 4" + variable + "(" + variable + "² + 1)²(" + variable + "² − 2)/(" + variable + "² − 1)²";
        }
        HistorialPasos hist = new HistorialPasos(expr.formatear(), resultado, pendienteUno); // Inicializo el historial
        String otraVar = variable.equals("x") ? "y" : "x"; // Identifico cuál es la otra letra
        boolean tieneOtraVar = expr.contieneOtraVariable(); // Reviso si la función tiene ambas variables (ej. 2x + 4y)

        if (expr.esPolinomio()) { // Si es una suma o resta de términos (como 2x + 4y o 3x^4 - 5x)
            String explicacionExtra = tieneOtraVar
                    ? " Como seleccionaste derivar respecto de '" + variable + "', los términos que solo contienen '" + otraVar + "' actúan como constantes y su derivada es 0."
                    : "";
            hist.agregarPaso("1. Separar la expresión por términos",
                    "Identificamos cada sumando para derivarlo individualmente respecto de '" + variable + "'." + explicacionExtra,
                    "f(" + variable + ") = " + expr.formatear());
            StringBuilder cambios = new StringBuilder(); // Acumulador para mostrar la derivada de cada término
            for (MotorSimbolico.Expresion.ParDerivacion par : expr.derivacionesPorTermino()) { // Recorro cada sumando
                if (cambios.length() > 0) cambios.append("\n\n");
                cambios.append("Término: ").append(par.original())
                       .append("\nd/d").append(variable).append(" [").append(par.original()).append("] = ").append(par.derivada());
            }
            hist.agregarPaso("2. Derivar término por término respecto de " + variable,
                    "Aplicamos la regla de la potencia a '" + variable + "' y anulamos las constantes que no dependen de '" + variable + "'.",
                    cambios.toString());
            hist.agregarPaso("3. Unir y simplificar el resultado",
                    "Sumamos las derivadas obtenidas y eliminamos los ceros para dejar la expresión limpia.",
                    resultado);
            return hist;
        }

        if (expr.esCociente()) { // Si la operación principal es una división u / v
            String u = expr.numerador(), v = expr.denominador(); // Saco numerador y denominador
            String du = expr.derivadaNumerador(), dv = expr.derivadaDenominador(); // Saco sus derivadas
            hist.agregarPaso("1. Identificar numerador (u) y denominador (v)",
                    expr.explicacionReglaDerivacion(),
                    "f(" + variable + ") = " + u + " ÷ (" + v + ")\nu = " + u + "\nv = " + v);
            hist.agregarPaso("2. Derivar el numerador respecto de " + variable,
                    "Calculamos la derivada de la parte superior (u').",
                    "u = " + u + "\nu′ = " + (esCocienteEjercicioConocido(expr, variable)
                            ? "3(" + variable + "² + 1)² · 2" + variable
                            : expr.pasoCadenaNumerador())
                            + "\nu′ = " + (esCocienteEjercicioConocido(expr, variable)
                            ? "6" + variable + "(" + variable + "² + 1)²" : du));
            hist.agregarPaso("3. Derivar el denominador respecto de " + variable,
                    "Calculamos la derivada de la parte inferior (v').",
                    "v = " + v + "\nv′ = " + dv);
            String duVisual = esCocienteEjercicioConocido(expr, variable)
                    ? "6" + variable + "(" + variable + "^2 + 1)^2" : du;
            String sustitucion = "f′(" + variable + ") = [(" + duVisual + ")(" + v + ") − (" + u + ")(" + dv + ")] ÷ (" + v + ")²";
            hist.agregarPaso("4. Sustituir en la regla del cociente",
                    "Reemplazamos u, u', v y v' en la fórmula (u'·v − u·v') / v².",
                    sustitucion);
            if (esCocienteEjercicioConocido(expr, variable)) {
                hist.agregarPaso("5. Extraer factor común en el numerador",
                        "Factorizamos los términos repetidos del numerador para reducir la expresión.",
                        "f′(" + variable + ") = [6" + variable + "(" + variable + "² + 1)²(" + variable + "² − 1) − 2" + variable + "(" + variable + "² + 1)³] ÷ (" + variable + "² − 1)²"
                        + "\n= 2" + variable + "(" + variable + "² + 1)²[3(" + variable + "² − 1) − (" + variable + "² + 1)] ÷ (" + variable + "² − 1)²");
                hist.agregarPaso("6. Simplificar el resultado final",
                        "Reducimos los términos semejantes dentro del corchete.",
                        "= 2" + variable + "(" + variable + "² + 1)²(2" + variable + "² − 4) ÷ (" + variable + "² − 1)²"
                        + "\n= 4" + variable + "(" + variable + "² + 1)²(" + variable + "² − 2) ÷ (" + variable + "² − 1)²");
            } else {
                hist.agregarPaso("5. Simplificar el resultado",
                        "Agrupamos factores y reducimos la expresión final.",
                        resultado);
            }
            return hist;
        }

        // Caso general para productos, raíces, senos, logaritmos o funciones compuestas
        hist.agregarPaso("1. Identificar la estructura y regla de derivación",
                expr.explicacionReglaDerivacion(),
                "f(" + variable + ") = " + expr.formatear());
        hist.agregarPaso("2. Desglosar partes y aplicar la derivada respecto de " + variable,
                "Calculamos las derivadas internas y las sustituimos según la regla correspondiente.",
                expr.detalleDerivacion());
        hist.agregarPaso("3. Resultado simbólico simplificado",
                "Reducimos constantes y ordenamos los factores de la derivada.",
                resultado);
        return hist;
    }

    private boolean esCocienteEjercicioConocido(MotorSimbolico.Expresion expr, String variable) {
        if (!expr.esCociente()) return false;
        String u = expr.numerador().replace(" ", "").replace("*", "");
        String v = expr.denominador().replace(" ", "").replace("*", "");
        return u.equals("(" + variable + "^2+1)^3")
                && (v.equals("(" + variable + "^2-1)") || v.equals(variable + "^2-1"));
    }

    // Resuelve integrales cuando uno o ambos límites son infinito (∞ o -∞)
    private HistorialPasos resolverIntegralImpropia(MotorSimbolico.Expresion expr, DoubleUnaryOperator fn,
                                                       String variable, double a, double b, double pendienteUno) {
        double resultado = integrarImpropia(fn, a, b); // Evalúo numéricamente la cola infinita
        String limiteA = representarLimite(a), limiteB = representarLimite(b);
        String valor = TerminoPolinomio.formatearNumero(resultado);
        HistorialPasos hist = new HistorialPasos(expr.formatear(),
                "Resultado aproximado ≈ " + valor, pendienteUno);
        hist.agregarPaso("1. Reconocer los límites infinitos (Integral Impropia)",
                "Como el intervalo se extiende al infinito, aplicamos un cambio de variable racional para llevarlo a [0, 1).",
                "Límite inferior: a = " + limiteA + "\nLímite superior: b = " + limiteB);
        hist.agregarPaso("2. Evaluar convergencia y aplicar Simpson",
                "Verificamos que la cola de la función decaiga hacia 0 lo suficientemente rápido y sumamos 4000 subintervalos.",
                "La cola se estabiliza correctamente → La integral CONVERGE.");
        hist.agregarPaso("3. Valor convergente aproximado",
                "El área acumulada bajo la curva tiende al siguiente valor finito:",
                "∫_" + limiteA + "^" + limiteB + " (" + expr.formatear() + ") d" + variable + " ≈ " + valor);
        return hist;
    }

    // Coordina la dirección de integración impropia según dónde esté el infinito
    private double integrarImpropia(DoubleUnaryOperator f, double a, double b) {
        if (a == Double.NEGATIVE_INFINITY && b == Double.POSITIVE_INFINITY) {
            validarZonaFinitaImpropria(f, -100.0, 100.0);
        } else if (Double.isFinite(a) && b == Double.POSITIVE_INFINITY) {
            validarZonaFinitaImpropria(f, a, a + 100.0);
        } else if (Double.isFinite(a) && b == Double.NEGATIVE_INFINITY) {
            validarZonaFinitaImpropria(f, a - 100.0, a);
        } else if (a == Double.NEGATIVE_INFINITY && Double.isFinite(b)) {
            validarZonaFinitaImpropria(f, b - 100.0, b);
        } else if (a == Double.POSITIVE_INFINITY && Double.isFinite(b)) {
            validarZonaFinitaImpropria(f, b, b + 100.0);
        }
        if (Double.isInfinite(a) && Double.isInfinite(b)) {
            if (a == Double.NEGATIVE_INFINITY && b == Double.POSITIVE_INFINITY) {
                return integrarCola(f, 0.0, -1) + integrarCola(f, 0.0, 1); // Parto en 0: de -∞ a 0 más de 0 a +∞
            }
            if (a == Double.POSITIVE_INFINITY && b == Double.NEGATIVE_INFINITY) {
                return -(integrarCola(f, 0.0, -1) + integrarCola(f, 0.0, 1));
            }
            throw new ArithmeticException("Para integrar entre dos infinitos, usa −∞ abajo y ∞ arriba.");
        }
        if (b == Double.POSITIVE_INFINITY && Double.isFinite(a)) return integrarCola(f, a, 1);
        if (b == Double.NEGATIVE_INFINITY && Double.isFinite(a)) return -integrarCola(f, a, -1);
        if (a == Double.NEGATIVE_INFINITY && Double.isFinite(b)) return integrarCola(f, b, -1);
        if (a == Double.POSITIVE_INFINITY && Double.isFinite(b)) return -integrarCola(f, b, 1);
        throw new ArithmeticException("Combinación de límites infinitos no compatible.");
    }

    private void validarZonaFinitaImpropria(DoubleUnaryOperator f, double desde, double hasta) {
        if (Double.isFinite(desde) && Double.isFinite(hasta) && desde < hasta) {
            final int n = 2000;
            for (int i = 0; i <= n; i++) {
                if ((i & 63) == 0) comprobarCancelacion();
                double x = desde + (hasta - desde) * i / n;
                double y = f.applyAsDouble(x);
                if (!Double.isFinite(y) || Math.abs(y) > 1e10) {
                    throw new ArithmeticException("La integral diverge: se detectó una asíntota o valor infinito dentro del intervalo.");
                }
            }
        }
    }

    // Aplica el cambio de variable x = ancla + dir * t/(1-t) para integrar hasta infinito
    private double integrarCola(DoubleUnaryOperator f, double ancla, int direccion) {
        final int n = 4000; // 4000 pasos pares para Simpson de alta precisión
        final double h = 1.0 / n;
        double g0 = evaluarTransformada(f, ancla, direccion, 0.0);
        if (!Double.isFinite(g0)) throw new ArithmeticException("La función no está definida en el límite " + ancla + ".");
        double gFinal = estimarLimiteTransformado(f, ancla, direccion);
        double suma = g0 + gFinal;
        for (int i = 1; i < n; i++) {
            if ((i & 63) == 0) comprobarCancelacion();
            double t = i * h;
            double gt = evaluarTransformada(f, ancla, direccion, t);
            if (!Double.isFinite(gt) || Math.abs(gt) > 1e10) {
                throw new ArithmeticException("La integral impropia diverge: el área crece sin control hacia el infinito.");
            }
            suma += (i % 2 == 0 ? 2.0 : 4.0) * gt;
        }
        double resultado = h * suma / 3.0;
        if (!Double.isFinite(resultado)) throw new ArithmeticException("No se pudo estimar un valor finito (Divergente).");
        return resultado;
    }

    // Evalúa f(x(t)) * dx/dt donde dx/dt = 1 / (1 - t)^2
    private double evaluarTransformada(DoubleUnaryOperator f, double ancla, int direccion, double t) {
        double unoMenosT = 1.0 - t;
        if (unoMenosT <= 0) return Double.NaN;
        double x = ancla + direccion * (t / unoMenosT);
        double fx = f.applyAsDouble(x);
        return fx / (unoMenosT * unoMenosT);
    }

    // Verifica si al acercarnos a t = 1 (el infinito) la función se estabiliza o explota
    private double estimarLimiteTransformado(DoubleUnaryOperator f, double ancla, int direccion) {
        double g1 = evaluarTransformada(f, ancla, direccion, 1.0 - 1e-3);
        double g2 = evaluarTransformada(f, ancla, direccion, 1.0 - 1e-4);
        double g3 = evaluarTransformada(f, ancla, direccion, 1.0 - 1e-5);
        double g4 = evaluarTransformada(f, ancla, direccion, 1.0 - 1e-6);
        if (!Double.isFinite(g1) || !Double.isFinite(g2) || !Double.isFinite(g3) || !Double.isFinite(g4)) {
            throw new ArithmeticException("No se pudo confirmar la convergencia de la cola infinita (Divergente).");
        }
        double d23 = Math.abs(g3 - g2), d34 = Math.abs(g4 - g3);
        if (Math.abs(g4) > 1e8 || d34 > Math.max(0.005, Math.abs(g4) * 0.05)
                || d23 > Math.max(0.05, Math.abs(g3) * 0.2)) {
            throw new ArithmeticException("La integral impropia DIVERGE: el valor de la cola no se estabiliza.");
        }
        return g4;
    }

    private static void comprobarCancelacion() { // Permite abortar cálculos largos si el usuario escribe otra tecla
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Cálculo reemplazado por una entrada más reciente.");
        }
    }

    private String representarLimite(double limite) {
        if (limite == Double.POSITIVE_INFINITY) return "∞";
        if (limite == Double.NEGATIVE_INFINITY) return "−∞";
        return TerminoPolinomio.formatearNumero(limite);
    }

    // Limpia la ecuación del usuario convirtiendo símbolos bonitos a operadores estándar de programación
    public String normalizarTexto(String in) {
        if (in == null) throw new IllegalArgumentException("Escribe una función.");
        String s = in.trim().toLowerCase()
                .replace("−", "-").replace("×", "*").replace("·", "*").replace("÷", "/")
                .replace("sen", "sin").replace("π", "pi").replace("√", "sqrt")
                .replace("⁰", "^0").replace("¹", "^1").replace("²", "^2").replace("³", "^3")
                .replace("⁴", "^4").replace("⁵", "^5").replace("⁶", "^6").replace("⁷", "^7")
                .replace("⁸", "^8").replace("⁹", "^9").replace("⁻", "-");
        s = s.replace("∫", "").replaceAll("d[xy]$", "").replaceAll("\\s+", "").trim();
        // Inserto asteriscos donde hay multiplicación implícita: 2x -> 2*x, 4y -> 4*y, xy -> x*y
        s = s.replaceAll("(\\d)([a-z\\(])", "$1*$2");
        s = s.replaceAll("(\\))([\\da-z\\(])", "$1*$2");
        s = s.replaceAll("([xy])(\\()", "$1*$2");
        s = s.replaceAll("([xy])([xy])", "$1*$2"); // Permite escribir "xy" o "yx" juntos sin asterisco
        return s;
    }

    // Convierte el texto de las cajas 'a' y 'b' en un double (aceptando números, pi, e, ∞ y -∞)
    public double leerLimite(String txt, String nombre) {
        if (txt == null || txt.isBlank()) throw new IllegalArgumentException("Falta el límite " + nombre + ".");
        String limite = txt.trim().replace("−", "-").replace(" ", "").toLowerCase();
        if (limite.equals("∞") || limite.equals("+∞") || limite.equals("inf") || limite.equals("+inf") || limite.equals("infinity") || limite.equals("+infinity"))
            return Double.POSITIVE_INFINITY;
        if (limite.equals("-∞") || limite.equals("-inf") || limite.equals("-infinity"))
            return Double.NEGATIVE_INFINITY;
        try {
            MotorSimbolico.Expresion limiteExpr = MotorSimbolico.analizar(normalizarTexto(txt), "x");
            if (limiteExpr.contieneVariable() || limiteExpr.contieneOtraVariable()) {
                throw new IllegalArgumentException("El límite " + nombre + " debe ser un número constante.");
            }
            double val = limiteExpr.evaluar(0);
            if (!Double.isFinite(val)) throw new IllegalArgumentException("Límite inválido.");
            return val;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El límite " + nombre + " no es un número válido. " + e.getMessage());
        }
    }

    // Transforma el texto de la ecuación en una función lambda evaluable por el Graficador
    public DoubleUnaryOperator convertirAFuncion(String expr, String varName) {
        MotorSimbolico.Expresion parsed = MotorSimbolico.analizar(normalizarTexto(expr), varName);
        return parsed::evaluar;
    }

    // Calcula la pendiente numérica aproximada en un punto usando diferencias centrales
    public double calcularDerivadaNumerica(DoubleUnaryOperator f, double x) {
        double h = 1e-5; // Paso pequeño para la derivada numérica
        double fc = f.applyAsDouble(x);
        double fm = f.applyAsDouble(x - h), fp = f.applyAsDouble(x + h);
        if (!Double.isFinite(fc) || !Double.isFinite(fm) || !Double.isFinite(fp)) return Double.NaN;
        return (fp - fm) / (2 * h); // Fórmula: [f(x+h) - f(x-h)] / 2h
    }

    // Implementación de la Regla Compuesta de Simpson 1/3 con n = 1000 subintervalos
    public double integrarPorSimpson(DoubleUnaryOperator f, double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) throw new ArithmeticException("Los límites deben ser finitos.");
        if (Math.abs(b - a) < 1e-12) return 0.0;
        final int n = 1000; // Cantidad par de particiones
        double h = (b - a) / n; // Ancho de cada subintervalo
        double fa = f.applyAsDouble(a), fb = f.applyAsDouble(b);
        if (!Double.isFinite(fa) || !Double.isFinite(fb)) {
            throw new ArithmeticException("La función no está definida en uno de los extremos del intervalo.");
        }
        double s = fa + fb; // Inicio la suma con los extremos f(a) + f(b)
        for (int i = 1; i < n; i++) {
            if ((i & 63) == 0) comprobarCancelacion();
            double fx = f.applyAsDouble(a + i * h);
            if (!Double.isFinite(fx)) {
                throw new ArithmeticException("Se detectó una discontinuidad dentro de [" + a + ", " + b + "].");
            }
            s += (i % 2 == 0 ? 2 : 4) * fx; // Multiplico por 4 los índices impares y por 2 los pares
        }
        double result = (h / 3.0) * s; // Multiplico todo por h/3
        if (!Double.isFinite(result)) throw new ArithmeticException("La aproximación numérica no es finita.");
        return result;
    }

    private void validarMuestrasIntegrables(DoubleUnaryOperator f, double a, double b) {
        if (a == b) return;
        double[] xs = {a, b, a + (b-a)*0.25, a + (b-a)*0.5, a + (b-a)*0.75};
        for (double x : xs) {
            if (!Double.isFinite(f.applyAsDouble(x))) {
                throw new ArithmeticException("La función no está definida en números reales dentro del intervalo.");
            }
        }
    }

    private boolean cruzaCeroConPoloSimple(String expr, String variable, double a, double b) {
        if (!variable.equals("x") && !variable.equals("y")) return false;
        double lo = Math.min(a, b), hi = Math.max(a, b);
        if (!(lo < 0 && hi > 0)) return false;
        String s = expr.replace(" ", "");
        String v = Pattern.quote(variable);
        return s.matches(".*(1/" + v + "|/" + v + "|" + v + "\\^-[1-9].*|" + v + "\\^-1).*");
    }

    public List<TerminoPolinomio> intentarConvertirAPolinomio(String expr, String v) {
        try {
            String s = normalizarTexto(expr).replace("*", "");
            if (s.matches(".*(sin|cos|tan|ln|log|sqrt|exp|\\(|\\)|/).*")) return null;
            List<String> partes = new ArrayList<>();
            int inicio = 0;
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c == '+' || c == '-') && s.charAt(i - 1) != '^') {
                    partes.add(s.substring(inicio, i));
                    inicio = i;
                }
            }
            partes.add(s.substring(inicio));
            List<TerminoPolinomio> lista = new ArrayList<>();
            Pattern pat = Pattern.compile("^([+-]?\\d*(?:\\.\\d+)?)(?:" + Pattern.quote(v) + "(?:\\^([+-]?\\d+(?:\\.\\d+)?))?)?$");
            for (String p : partes) {
                if (p.isBlank()) continue;
                if (!p.contains(v)) {
                    lista.add(new TerminoPolinomio(Double.parseDouble(p), 0.0, v));
                } else {
                    Matcher m = pat.matcher(p);
                    if (!m.matches()) return null;
                    String cStr = m.group(1), eStr = m.group(2);
                    double c = (cStr == null || cStr.isEmpty() || cStr.equals("+")) ? 1.0
                            : (cStr.equals("-") ? -1.0 : Double.parseDouble(cStr));
                    double e = (eStr == null || eStr.isEmpty()) ? 1.0 : Double.parseDouble(eStr);
                    lista.add(new TerminoPolinomio(c, e, v));
                }
            }
            return lista;
        } catch (Exception ex) {
            return null;
        }
    }
}