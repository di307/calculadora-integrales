package calculadora.logica; // Paquete de lógica de la calculadora

public class TerminoPolinomio { // Clase que representa un término del tipo: coeficiente * variable^exponente

    private final double coeficiente; // Número que multiplica a la variable
    private final double exponente; // Potencia a la que está elevada la variable
    private final String variable; // Nombre de la variable ("x" o "y")
    private final boolean esLogaritmo; // Bandera que se activa si al integrar x^-1 pasa a ser ln|x|

    // Constructor básico para términos normales (no logarítmicos)
    public TerminoPolinomio(double coeficiente, double exponente, String variable) {
        this(coeficiente, exponente, variable, false); // Llamo al constructor completo pasando false en esLogaritmo
    }

    // Constructor completo que inicializa todos los atributos del término
    public TerminoPolinomio(double coeficiente, double exponente, String variable, boolean esLogaritmo) {
        this.coeficiente = coeficiente; // Guardo el coeficiente numérico
        this.exponente = exponente; // Guardo el exponente
        this.variable = (variable == null || variable.isBlank()) ? "x" : variable; // Si no viene variable, uso "x" por defecto
        this.esLogaritmo = esLogaritmo; // Guardo si es un término logarítmico
    }

    // Método que deriva este término usando la regla de la potencia: c * n * x^(n-1)
    public TerminoPolinomio derivar() {
        if (Math.abs(exponente) < 1e-9) { // Si el exponente es 0 (es una constante sola)
            return new TerminoPolinomio(0, 0, variable); // La derivada de una constante devuelve 0
        }
        return new TerminoPolinomio(coeficiente * exponente, exponente - 1.0, variable); // Multiplico coef por exp y resto 1 al grado
    }

    // Método que integra este término usando la regla de la potencia: (c / (n+1)) * x^(n+1)
    public TerminoPolinomio integrar() throws ArithmeticException {
        if (Math.abs(exponente + 1.0) < 1e-9) { // Si el exponente es -1 (caso 1/x)
            return new TerminoPolinomio(coeficiente, 1.0, variable, true); // Devuelvo el término marcado como logaritmo natural
        }
        double nuevoExp = exponente + 1.0; // Sumo 1 al exponente actual
        if (nuevoExp == 0) { // Validación extra por seguridad contra división por cero
            throw new ArithmeticException("División entre cero al integrar."); // Lanzo error aritmético si ocurre
        }
        return new TerminoPolinomio(coeficiente / nuevoExp, nuevoExp, variable, false); // Divido el coeficiente entre el nuevo exponente
    }

    // Método para evaluar cuánto vale el término cuando la variable toma un número real
    public double evaluar(double valor) throws ArithmeticException {
        if (Math.abs(coeficiente) < 1e-12) return 0.0; // Si el coeficiente es 0, todo el término vale 0
        if (esLogaritmo) { // Si este término es un ln|x|
            if (Math.abs(valor) < 1e-12) { // Reviso que no intenten evaluar ln(0)
                throw new ArithmeticException("Logaritmo natural no definido en 0."); // Lanzo error porque ln(0) no existe
            }
            return coeficiente * Math.log(Math.abs(valor)); // Calculo coeficiente * ln(|valor|)
        }
        if (valor == 0.0 && exponente < 0) { // Si evalúan 0 con exponente negativo (ej. 1/0)
            throw new ArithmeticException("División por cero al evaluar exponente negativo."); // Bloqueo la división entre cero
        }
        return coeficiente * Math.pow(valor, exponente); // Calculo coeficiente * (valor ^ exponente)
    }

    public double getCoeficiente() { return coeficiente; } // Getter del coeficiente
    public double getExponente() { return exponente; } // Getter del exponente
    public String getVariable() { return variable; } // Getter de la variable

    // Convierte el término a texto limpio cuidando los signos "+" y "-"
    public String formatear(boolean esPrimero) {
        if (Math.abs(coeficiente) < 1e-9) return esPrimero ? "0" : ""; // Si vale 0 y no es el único término, no imprimo nada

        String signo = coeficiente < 0 ? (esPrimero ? "-" : " - ") : (esPrimero ? "" : " + "); //Elijo el signo según su posición
        double absCoef = Math.abs(coeficiente); // Tomo el valor absoluto porque el signo ya lo manejé arriba
        String strCoef = formatearNumero(absCoef); // Formateo el número a entero, fracción o decimal

        if (esLogaritmo) { // Si es un término con logaritmo
            String pref = (Math.abs(absCoef - 1.0) < 1e-9) ? "" : strCoef; // Omito el 1 si es 1*ln|x|
            return signo + pref + "ln|" + variable + "|"; // Devuelvo el logaritmo armado
        }
        if (Math.abs(exponente) < 1e-9) return signo + strCoef; // Si el exponente es 0, solo muestro el número constante

        String prefijoCoef = (Math.abs(absCoef - 1.0) < 1e-9) ? "" : strCoef; // Omito el coeficiente si es 1 (para escribir "x" y no "1x")
        if (Math.abs(exponente - 1.0) < 1e-9) return signo + prefijoCoef + variable; // Si el exponente es 1, no pongo exponente

        return signo + prefijoCoef + variable + aSuperindice(exponente); // Armo el término completo con su superíndice
    }

    // Convierte cualquier decimal en entero exacto, fracción limpia o decimal de 3 cifras
    public static String formatearNumero(double num) {
        if (Math.abs(num - Math.rint(num)) < 1e-7) { // Si el número no tiene parte decimal significativa
            return String.valueOf((long) Math.rint(num)); // Lo devuelvo como número entero largo
        }
        for (int den = 2; den <= 12; den++) { // Pruebo denominadores del 2 al 12 para ver si es una fracción exacta
            double prod = num * den; // Multiplico el decimal por el denominador de prueba
            if (Math.abs(prod - Math.rint(prod)) < 1e-6) { // Si el resultado da un entero exacto
                return "(" + (long) Math.rint(prod) + "/" + den + ")"; // Lo devuelvo escrito como fracción (num/den)
            }
        }
        return String.format(java.util.Locale.US, "%.3f", num); // Si no es fracción simple, lo dejo con 3 decimales
    }

    // Formatea el exponente usando caracteres superíndice para grados comunes
    private String aSuperindice(double exp) {
        if (Math.abs(exp - Math.rint(exp)) < 1e-7) { // Verifico si el exponente es un número entero
            long e = (long) Math.rint(exp); // Redondeo al entero más cercano
            if (e == 2) return "²"; // Uso superíndice 2 directo
            if (e == 3) return "³"; // Uso superíndice 3 directo
            if (e == 4) return "⁴"; // Uso superíndice 4 directo
            return "^" + e; // Para otros enteros lo dejo con ^ para que la ventana lo convierta
        }
        return "^(" + formatearNumero(exp) + ")"; // Si el exponente es fracción, lo encierro entre paréntesis
    }
}