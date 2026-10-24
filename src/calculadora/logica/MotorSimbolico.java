package calculadora.logica; // Defino el paquete de lógica matemática

import java.math.BigDecimal; // Uso esto para redondear sin arrastrar decimales basura
import java.util.Locale; // Me aseguro de que las minúsculas funcionen en cualquier PC
import java.util.ArrayList; // Manejo de listas dinámicas
import java.util.LinkedHashMap; // Mapas que guardan el orden de inserción
import java.util.List; // Interfaz de listas
import java.util.Map; // Interfaz de mapas
import java.util.TreeMap; // Mapa que ordena todo automáticamente (útil para grados de polinomios)

public final class MotorSimbolico { // Mi motor matemático principal (CAS)
    private MotorSimbolico() {} // Lo hago privado porque solo usaré métodos estáticos

    // Punto de entrada: recibo el texto y la variable, y armo el árbol AST
    public static Expresion analizar(String texto, String variable) {
        if (variable == null || !(variable.equals("x") || variable.equals("y"))) {
            throw new IllegalArgumentException("La variable debe ser x o y."); // Bloqueo si mandan otra letra
        }
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Escribe una función.");
        Parser parser = new Parser(texto, variable); // Instancio mi analizador sintáctico
        Node raiz = parser.parse(); // Convierto el texto a un árbol de nodos
        return new Expresion(raiz, variable); // Retorno el objeto listo para operar
    }

    public static final class Expresion { // Clase que envuelve el árbol y da métodos fáciles de usar
        private final Node raiz; // El nodo principal de la ecuación
        private final String variable; // La letra con la que estoy operando
        private Expresion(Node raiz, String variable) { this.raiz = raiz; this.variable = variable; }

        public double evaluar(double x) { return raiz.eval(x, variable); } // Evalúo la función con un número real
        public Expresion derivar() { return new Expresion(raiz.derivar(variable), variable); } // Pido la derivada al árbol
        public Expresion integrar() { return new Expresion(MotorSimbolico.integrar(raiz, variable), variable); } // Pido la integral al motor
        public String formatear() { return raiz.str(); } // Traduzco el árbol de nuevo a texto
        public boolean contieneVariable() { return raiz.depende(variable); } // Reviso si la función tiene mi variable activa
        public boolean contieneOtraVariable() { // Reviso si hay otra variable mezclada (ej. y)
            String otra = variable.equals("x") ? "y" : "x"; 
            return raiz.depende(otra); 
        }

        public boolean esCociente() { return raiz.tipo.equals("/"); } // Compruebo si la operación principal es división
        public String numerador() { exigirCociente(); return raiz.a.str(); } // Saco el texto de la parte de arriba
        public String denominador() { exigirCociente(); return raiz.b.str(); } // Saco el texto de la parte de abajo
        public String derivadaNumerador() { exigirCociente(); return raiz.a.derivar(variable).str(); } // Derivo solo arriba
        public String derivadaDenominador() { exigirCociente(); return raiz.b.derivar(variable).str(); } // Derivo solo abajo
        
        public String pasoCadenaNumerador() { // Genera el paso intermedio de la regla de la cadena para el cociente
            exigirCociente(); 
            Node u = raiz.a; 
            if (u.tipo.equals("^") && u.b.esNumero()) { // Si el numerador es una potencia (u^n)
                String n = u.b.str(); 
                String nuevoExp = formatoNumero(u.b.numero - 1); 
                return n + " · (" + u.a.str() + ")^" + nuevoExp + " · (" + u.a.derivar(variable).str() + ")"; 
            }
            return derivadaNumerador(); 
        }
        
        public boolean esSumaORestaEnRaiz() { return raiz.tipo.equals("+") || raiz.tipo.equals("-"); } 
        public boolean esPolinomio() { return esPolinomioNodo(raiz, variable); } // Reviso si toda la función es polinómica
        
        public List<ParDerivacion> derivacionesPorTermino() { // Separo términos y derivo uno por uno
            List<SignedNode> terms = new ArrayList<>(); 
            recogerTerminos(raiz, 1, terms); 
            List<ParDerivacion> salida = new ArrayList<>(); 
            for (SignedNode term : terms) { 
                String original = formatearConSigno(term.node, term.sign); 
                Node derivada = term.node.derivar(variable); 
                if (term.sign < 0) derivada = neg(derivada); // Le devuelvo el menos si era resta
                salida.add(new ParDerivacion(original, derivada.str())); 
            }
            return salida; 
        }
        
        public List<ParIntegracion> integracionesPorTermino() { // Separo términos e integro uno por uno
            List<SignedNode> terms = new ArrayList<>(); 
            recogerTerminos(raiz, 1, terms); 
            List<ParIntegracion> salida = new ArrayList<>(); 
            for (SignedNode term : terms) { 
                String original = formatearConSigno(term.node, term.sign); 
                Node primitiva = MotorSimbolico.integrar(term.node, variable); 
                if (term.sign < 0) primitiva = neg(primitiva); 
                salida.add(new ParIntegracion(original, primitiva.str())); 
            }
            return salida; 
        }
        
        private void exigirCociente() { 
            if (!esCociente()) throw new IllegalStateException("La expresión no es un cociente."); 
        }
        
        public record ParDerivacion(String original, String derivada) {} 
        public record ParIntegracion(String original, String primitiva) {} 
        
        public boolean haySingularidadConocida(double a, double b) { // Busco asíntotas entre los límites de la integral
            if (a == b) return false; 
            return raiz.haySingularidad(variable, Math.min(a, b), Math.max(a, b)); 
        }

        public String explicacionReglaDerivacion() { // Devuelvo el teorema teórico para derivar esta función
            String otra = variable.equals("x") ? "y" : "x"; 
            String notaConstante = raiz.depende(otra) 
                    ? " Nota: como derivamos respecto de '" + variable + "', la variable '" + otra + "' actúa como una constante."
                    : ""; 
            Node v = raiz.b; 
            return (switch (raiz.tipo) { 
                case "+", "-" -> "Regla de la linealidad (suma/resta): (u " + raiz.tipo + " v)' = u' " + raiz.tipo + " v'. Derivamos cada término por separado.";
                case "*" -> "Regla del producto: (u · v)' = u' · v + u · v'.";
                case "/" -> "Regla del cociente: (u / v)' = (u' · v − u · v') / v².";
                case "^" -> v != null && !v.depende(variable)
                        ? "Regla de la potencia y de la cadena: (uⁿ)' = n · uⁿ⁻¹ · u'."
                        : "Regla general de la potencia: (uᵛ)' = uᵛ · (v' · ln(u) + v · u' / u).";
                case "neg" -> "Regla del factor constante negativo: (-u)' = -u'.";
                case "sin" -> "Regla de la cadena para el seno: (sen u)' = cos(u) · u'.";
                case "cos" -> "Regla de la cadena para el coseno: (cos u)' = -sen(u) · u'.";
                case "tan" -> "Regla de la cadena para la tangente: (tan u)' = u' / cos²(u).";
                case "ln" -> "Regla de la cadena para logaritmo natural: (ln u)' = u' / u.";
                case "log" -> "Regla de la cadena para logaritmo base 10: (log₁₀ u)' = u' / (u · ln(10)).";
                case "exp" -> "Regla de la cadena para la exponencial: (eᵘ)' = eᵘ · u'.";
                case "sqrt" -> "Regla de la cadena para raíz cuadrada: (√u)' = u' / (2√u).";
                case "abs" -> "Derivada de valor absoluto: (|u|)' = (u / |u|) · u'.";
                default -> !raiz.depende(variable)
                        ? "Regla de la constante: cualquier término que no contenga '" + variable + "' tiene derivada igual a 0."
                        : "Regla de la identidad: la derivada de '" + variable + "' respecto de sí misma es 1.";
            }) + notaConstante; 
        }

        public String detalleDerivacion() { // Armo el paso a paso sustituyendo u, v, u', v'
            Node u = raiz.a, v = raiz.b; 
            Node du = u == null ? null : u.derivar(variable); 
            Node dv = v == null ? null : v.derivar(variable); 
            return switch (raiz.tipo) { 
                case "+", "-" -> "Identificamos las partes:\n  u = " + u.str() + "   →   u' = " + du.str()
                        + "\n  v = " + v.str() + "   →   v' = " + dv.str()
                        + "\n\nSustituyendo en u' " + raiz.tipo + " v':\n  f'(" + variable + ") = " + raiz.derivar(variable).str();
                case "*" -> "Identificamos los factores del producto:\n  u = " + u.str() + "   →   u' = " + du.str()
                        + "\n  v = " + v.str() + "   →   v' = " + dv.str()
                        + "\n\nSustituyendo en (u' · v) + (u · v'):\n  f'(" + variable + ") = (" + du.str() + ")(" + v.str() + ") + (" + u.str() + ")(" + dv.str() + ")";
                case "/" -> "Identificamos numerador (u) y denominador (v):\n  u = " + u.str() + "   →   u' = " + du.str()
                        + "\n  v = " + v.str() + "   →   v' = " + dv.str()
                        + "\n\nSustituyendo en (u'·v − u·v') / v²:\n  f'(" + variable + ") = [(" + du.str() + ")(" + v.str() + ") − (" + u.str() + ")(" + dv.str() + ")] / (" + v.str() + ")²";
                case "^" -> v != null && !v.depende(variable)
                        ? "Identificamos la base interior (u) y el exponente (n):\n  u = " + u.str() + "   →   u' = " + du.str() + "\n  n = " + v.str()
                        + "\n\nBajamos el exponente, restamos 1 al grado y multiplicamos por la derivada interna u':\n  f'(" + variable + ") = " + v.str() + " · (" + u.str() + ")^(" + num(v.numero - 1).str() + ") · (" + du.str() + ")"
                        : "Aplicando derivación logarítmica general sobre la base y el exponente:\n  f'(" + variable + ") = " + raiz.derivar(variable).str();
                case "sin", "cos", "tan", "ln", "log", "exp", "sqrt", "abs" -> "Identificamos el argumento interior (u) y su derivada (u'):\n  u = " + u.str() + "\n  u' = " + du.str()
                        + "\n\nDerivamos la función exterior y multiplicamos por u' (Regla de la Cadena):\n  f'(" + variable + ") = " + raiz.derivar(variable).str();
                case "neg" -> "Conservamos el signo negativo y derivamos el interior:\n  f'(" + variable + ") = -(" + du.str() + ")";
                default -> "f'(" + variable + ") = " + raiz.derivar(variable).str();
            };
        }

        public String explicacionIntegracion() { // Devuelvo el teorema teórico para integrar
            String otra = variable.equals("x") ? "y" : "x"; 
            String notaConstante = raiz.depende(otra) 
                    ? " Como integramos respecto de '" + variable + "', la variable '" + otra + "' se trata como constante."
                    : "";
            if (!raiz.depende(variable)) return "Regla de la constante: ∫ k d" + variable + " = k · " + variable + " + C." + notaConstante;
            if (raiz.tipo.equals("+") || raiz.tipo.equals("-")) return "Propiedad de Linealidad: ∫ [f(" + variable + ") ± g(" + variable + ")] d" + variable + " = ∫ f(" + variable + ") d" + variable + " ± ∫ g(" + variable + ") d" + variable + ".";
            if (raiz.tipo.equals("*") && (!raiz.a.depende(variable) || !raiz.b.depende(variable))) return "Propiedad del Múltiplo Constante: ∫ k · f(" + variable + ") d" + variable + " = k · ∫ f(" + variable + ") d" + variable + ". Extraemos la constante fuera de la integral.";
            if (raiz.tipo.equals("*") && integrarProductoPorSustitucion(raiz, variable) != null) return "Método de Sustitución: ∫ g(u) · u' d" + variable + " = ∫ g(u) du. Un factor es proporcional a la derivada del argumento interior.";
            if (raiz.tipo.equals("/") && intentarSustitucionCociente(raiz.a, raiz.b, variable) != null) return "Método de Sustitución en Cociente: ∫ [g'(x) / f(g(x))] dx. El numerador es proporcional a la derivada del interior del denominador.";
            if (raiz.tipo.equals("^") && raiz.a.tipo.equals("var") && raiz.a.nombre.equals(variable) && !raiz.b.depende(variable)) {
                return (raiz.b.esNumero() && Math.abs(raiz.b.numero + 1) < 1e-10)
                        ? "Regla del Recíproco (Logarítmica): ∫ " + variable + "⁻¹ d" + variable + " = ln|" + variable + "| + C."
                        : "Regla de la Potencia para Integrales: ∫ " + variable + "ⁿ d" + variable + " = " + variable + "ⁿ⁺¹ / (n + 1) + C   (con n ≠ -1).";
            }
            if (raiz.tipo.equals("^") && raiz.a.esNumero() && Math.abs(raiz.a.numero - Math.E) < 1e-10) {
                return "Regla Exponencial Lineal: ∫ e^(a·" + variable + "+b) d" + variable + " = e^(a·" + variable + "+b) / a + C.";
            }
            if (raiz.tipo.equals("/") && !raiz.b.depende(variable)) {
                return "División entre constante: ∫ [u / c] d" + variable + " = (1 / c) · ∫ u d" + variable + ".";
            }
            if (raiz.tipo.equals("/") && !raiz.a.depende(variable) && raiz.b.tipo.equals("var") && raiz.b.nombre.equals(variable)) {
                return "Integración Logarítmica Directa: ∫ (k / " + variable + ") d" + variable + " = k · ln|" + variable + "| + C." + notaConstante;
            }
            return switch (raiz.tipo) {
                case "sin" -> "Integral Trigonométrica del Seno: ∫ sen(u) d" + variable + " = -cos(u) / u' + C.";
                case "cos" -> "Integral Trigonométrica del Coseno: ∫ cos(u) d" + variable + " = sen(u) / u' + C.";
                case "tan" -> "Integral Trigonométrica de la Tangente: ∫ tan(u) d" + variable + " = -ln|cos(u)| / u' + C.";
                case "exp" -> "Integral Exponencial Natural: ∫ eᵘ d" + variable + " = eᵘ / u' + C.";
                case "ln" -> "Integración por Partes del Logaritmo Natural: ∫ ln(u) d" + variable + " = (u · ln|u| − u) / u' + C.";
                case "sqrt" -> "Integral de Raíz Cuadrada (como potencia u^(1/2)): ∫ √u d" + variable + " = (2/3) · u^(3/2) / u' + C.";
                default -> "Aplicación de reglas elementales de antiderivación y simplificación algebraica.";
            };
        }
    }

    // Aquí integro recursivamente el árbol. Es el corazón de las integrales indefinidas.
    private static Node integrar(Node n, String variable) {
        if (!n.depende(variable)) return mul(n, var(variable)); // Si es constante, le pego la variable
        
        switch (n.tipo) {
            case "var":
                return div(pow(var(variable), num(2)), num(2)); // x -> x^2 / 2
            case "neg":
                return neg(integrar(n.a, variable)); // -u -> - integral(u)
            case "+":
                return add(integrar(n.a, variable), integrar(n.b, variable)); // Separo sumas
            case "-":
                return sub(integrar(n.a, variable), integrar(n.b, variable)); // Separo restas
            case "*": {
                Node porSustitucion = integrarProductoPorSustitucion(n, variable); // Intento u-substitution en multiplicación
                if (porSustitucion != null) return porSustitucion;
                if (!n.a.depende(variable)) return mul(n.a, integrar(n.b, variable)); // Saco constante izquierda
                if (!n.b.depende(variable)) return mul(n.b, integrar(n.a, variable)); // Saco constante derecha
                throw noIntegrable(n);
            }
            case "/": {
                // PRIMERO: Intento la nueva sustitución para cocientes (ej. 3x / sqrt(x^2+9))
                Node porSustitucion = intentarSustitucionCociente(n.a, n.b, variable);
                if (porSustitucion != null) return porSustitucion;

                Node reducida = reducirCocienteDeMonomios(n, variable); // Simplifico potencias si se puede
                if (reducida != null && !reducida.str().equals(n.str())) {
                    return integrar(reducida, variable);
                }
                if (!n.b.depende(variable)) return div(integrar(n.a, variable), n.b); // Saco divisor constante
                if (!n.a.depende(variable) && n.b.tipo.equals("var")) {
                    return mul(n.a, fn("ln", fn("abs", n.b))); // k/x -> k*ln|x|
                }
                if (!n.a.depende(variable) && n.b.tipo.equals("^")
                        && n.b.a.tipo.equals("var") && !n.b.b.depende(variable)) {
                    return mul(n.a, integrar(pow(n.b.a, neg(n.b.b)), variable)); // k/x^n -> k*x^-n
                }
                throw noIntegrable(n);
            }
            case "^": {
                if (n.b.depende(variable)) {
                    if (n.a.esNumero() && Math.abs(n.a.numero - Math.E) < 1e-10) {
                        double slope = slopeOf(n.b, variable); // Si es e^(ax+b), verifico si el exponente es lineal
                        if (Double.isFinite(slope) && Math.abs(slope) > 1e-12) {
                            return div(fn("exp", n.b), num(slope)); // e^u / u'
                        }
                    }
                    throw noIntegrable(n);
                }
                double p = n.b.numero;
                double slope = slopeOf(n.a, variable); // Reviso si la base es lineal (mx+b)
                if (!Double.isFinite(slope) || Math.abs(slope) < 1e-12) throw noIntegrable(n); // Si no es lineal, rechazo
                if (Math.abs(p + 1) < 1e-10) return div(fn("ln", fn("abs", n.a)), num(slope)); // Caso exponente -1
                return div(pow(n.a, num(p + 1)), num(slope * (p + 1))); // Regla de la potencia con regla de la cadena invertida
            }
            case "sin", "cos", "tan", "exp", "ln", "log", "sqrt", "abs": {
                double slope = slopeOf(n.a, variable); // Saco la derivada del interior
                if (!Double.isFinite(slope) || Math.abs(slope) < 1e-12) throw noIntegrable(n);
                Node u = n.a;
                return switch (n.tipo) {
                    case "sin" -> div(neg(fn("cos", u)), num(slope));
                    case "cos" -> div(fn("sin", u), num(slope));
                    case "tan" -> div(neg(fn("ln", fn("abs", fn("cos", u)))), num(slope));
                    case "exp" -> div(fn("exp", u), num(slope));
                    case "ln" -> div(sub(mul(u, fn("ln", u)), u), num(slope));
                    case "log" -> div(sub(mul(u, fn("ln", u)), u), num(slope * Math.log(10)));
                    case "sqrt" -> div(mul(num(2.0 / 3.0), pow(u, num(1.5))), num(slope));
                    case "abs" -> div(mul(u, fn("abs", u)), num(2 * slope));
                    default -> throw noIntegrable(n);
                };
            }
            default:
                throw noIntegrable(n);
        }
    }

    // Método que busca si el producto tiene la forma f(g(x)) * g'(x)
    private static Node integrarProductoPorSustitucion(Node producto, String variable) {
        Node resultado = intentarSustitucion(producto.a, producto.b, variable); 
        if (resultado != null) return resultado;
        return intentarSustitucion(producto.b, producto.a, variable); 
    }

    // Analiza si un lado es la derivada del interior del otro lado
    private static Node intentarSustitucion(Node factor, Node externa, String variable) {
        if (factor == null || externa == null) return null;
        Node u;
        if (externa.tipo.equals("^") && externa.a.esNumero()
                && Math.abs(externa.a.numero - Math.E) < 1e-10) {
            u = externa.b; // e^(u) -> tomo u
        } else if (externa.tipo.equals("^") && !externa.b.depende(variable)) {
            u = externa.a; // u^n -> tomo u
        } else if (externa.tipo.equals("sin") || externa.tipo.equals("cos")
                || externa.tipo.equals("tan") || externa.tipo.equals("exp")
                || externa.tipo.equals("ln") || externa.tipo.equals("log")
                || externa.tipo.equals("sqrt") || externa.tipo.equals("abs")) {
            u = externa.a; // func(u) -> tomo u
        } else {
            return null;
        }
        if (u == null || !u.depende(variable)) return null;

        Node du = u.derivar(variable); // Derivo u
        Double k = factorRelativoADerivada(factor, du, variable); // Reviso si el factor suelto es múltiplo de du
        if (k == null || !Double.isFinite(k)) return null;

        Node primitiva;
        switch (externa.tipo) {
            case "^":
                if (externa.a.esNumero() && Math.abs(externa.a.numero - Math.E) < 1e-10) {
                    primitiva = fn("exp", u); 
                } else {
                    if (externa.b.esNumero() && Math.abs(externa.b.numero + 1.0) < 1e-10) {
                        primitiva = fn("ln", fn("abs", u));
                    } else {
                        Node nMasUno = add(externa.b, num(1.0));
                        primitiva = div(pow(u, nMasUno), nMasUno);
                    }
                }
                break;
            case "sin": primitiva = neg(fn("cos", u)); break;
            case "cos": primitiva = fn("sin", u); break;
            case "tan": primitiva = neg(fn("ln", fn("abs", fn("cos", u)))); break;
            case "exp": primitiva = fn("exp", u); break;
            case "ln": primitiva = sub(mul(u, fn("ln", u)), u); break;
            case "log": primitiva = div(sub(mul(u, fn("ln", u)), u), num(Math.log(10))); break;
            case "sqrt": primitiva = div(mul(num(2), pow(u, num(1.5))), num(3)); break;
            case "abs": primitiva = div(mul(u, fn("abs", u)), num(2)); break;
            default: return null;
        }
        return Math.abs(k - 1.0) < 1e-10 ? primitiva : mul(num(k), primitiva); // Multiplico la primitiva por la constante k que sobró
    }

    // ¡NUEVO MÉTODO PARA RESOLVER TU EJERCICIO! Analiza si el numerador es la derivada de algo del denominador
    private static Node intentarSustitucionCociente(Node numerador, Node denominador, String variable) {
        if (numerador == null || denominador == null) return null; 
        
        Node u = null; // Busco mi candidato a variable u en el denominador
        if (denominador.tipo.equals("sqrt")) {
            u = denominador.a; // Si el denominador es √(u), tomo lo de adentro
        } else if (denominador.tipo.equals("^") && !denominador.b.depende(variable)) {
            u = denominador.a; // Si el denominador es u^n, tomo la base
        } else if (denominador.depende(variable)) {
            u = denominador; // Si el denominador es un polinomio normal, u es todo el denominador
        }
        
        if (u == null || !u.depende(variable)) return null; 
        
        Node du = u.derivar(variable); // Derivo u para obtener du
        Double k = factorRelativoADerivada(numerador, du, variable); // Verifico si el numerador es proporcional a du
        if (k == null || !Double.isFinite(k)) return null; // Si no encaja, aborto la sustitución
        
        Node primitiva;
        if (denominador.tipo.equals("sqrt")) {
            primitiva = mul(num(2), fn("sqrt", u)); // Integral de k*du / √u = k * 2√u
        } else if (denominador.tipo.equals("^") && denominador.b.esNumero()) {
            double p = denominador.b.numero;
            if (Math.abs(p - 1.0) < 1e-10) {
                primitiva = fn("ln", fn("abs", u)); // Integral de k*du / u^1 = k * ln|u|
            } else {
                Node nuevoExp = num(-p + 1.0); // Subo el exponente como negativo y le sumo 1
                primitiva = div(pow(u, nuevoExp), nuevoExp); // Aplico regla de la potencia
            }
        } else {
            primitiva = fn("ln", fn("abs", u)); // Integral de k*du / u = k * ln|u|
        }
        
        return Math.abs(k - 1.0) < 1e-10 ? primitiva : mul(num(k), primitiva); // Adjunto la constante y termino
    }

    // Calcula si dos nodos polinómicos son proporcionales (ej. 3x y 2x -> k = 1.5)
    private static Double factorRelativoADerivada(Node factor, Node derivada, String variable) {
        if (factor.str().equals(derivada.str())) return 1.0; 
        Monomio f = extraerMonomio(factor, variable); 
        Monomio d = extraerMonomio(derivada, variable); 
        if (f == null || d == null || Math.abs(d.coeficiente) < 1e-12
                || Math.abs(f.exponente - d.exponente) > 1e-10) return null; 
        return f.coeficiente / d.coeficiente; 
    }

    private record Monomio(double coeficiente, double exponente) {} 

    // Reduce potencias idénticas antes de integrar (x^4 / x^2 -> x^2)
    private static Node reducirCocienteDeMonomios(Node cociente, String variable) {
        if (cociente == null || !cociente.tipo.equals("/")) return null; 
        String otra = variable.equals("x") ? "y" : "x"; 
        if (cociente.depende(otra)) return null; // Solo lo hago si no hay variables mezcladas
        Monomio numerador = extraerMonomio(cociente.a, variable); 
        Monomio denominador = extraerMonomio(cociente.b, variable); 
        if (numerador == null || denominador == null
                || Math.abs(denominador.coeficiente) < 1e-12) return null; 
        double coef = numerador.coeficiente / denominador.coeficiente; 
        double exp = numerador.exponente - denominador.exponente; 
        if (!Double.isFinite(coef) || !Double.isFinite(exp)) return null; 
        if (Math.abs(coef) < 1e-12) return num(0); 
        Node variableParte; 
        if (Math.abs(exp) < 1e-10) variableParte = num(1); 
        else if (Math.abs(exp - 1.0) < 1e-10) variableParte = var(variable); 
        else variableParte = pow(var(variable), num(exp)); 
        return mul(num(coef), variableParte); 
    }

    // Analiza un nodo y me dice si es un monomio limpio (coeficiente y exponente)
    private static Monomio extraerMonomio(Node n, String variable) {
        if (n == null) return null; 
        String otra = variable.equals("x") ? "y" : "x"; 
        if (n.depende(otra)) return null; 
        if (!n.depende(variable)) { 
            double c = n.eval(0, variable); 
            return Double.isFinite(c) ? new Monomio(c, 0.0) : null; 
        }
        return switch (n.tipo) {
            case "var" -> n.nombre.equals(variable) ? new Monomio(1.0, 1.0) : null; 
            case "neg" -> { 
                Monomio m = extraerMonomio(n.a, variable); 
                yield m == null ? null : new Monomio(-m.coeficiente, m.exponente); 
            }
            case "^" -> n.a.tipo.equals("var") && n.a.nombre.equals(variable) && n.b.esNumero()
                    ? new Monomio(1.0, n.b.numero) : null; 
            case "*" -> { 
                Monomio izq = extraerMonomio(n.a, variable); 
                Monomio der = extraerMonomio(n.b, variable); 
                yield izq == null || der == null ? null : new Monomio(
                        izq.coeficiente * der.coeficiente, izq.exponente + der.exponente); 
            }
            case "/" -> { 
                Monomio izq = extraerMonomio(n.a, variable);
                Monomio der = extraerMonomio(n.b, variable);
                yield izq == null || der == null || Math.abs(der.coeficiente) < 1e-12
                        ? null : new Monomio(izq.coeficiente / der.coeficiente,
                                izq.exponente - der.exponente); 
            }
            default -> null; 
        };
    }

    // Texto de advertencia si la integral excede nuestras capacidades simbólicas
    private static IllegalArgumentException noIntegrable(Node n) {
        return new IllegalArgumentException("Todavía no hay una regla simbólica directa para integrar «" + n.str()
                + "». Si buscas evaluar un intervalo, selecciona 'Integral definida [a, b]' para usar el método numérico de Simpson.");
    }

    // Saca la pendiente para ver si el interior es una función lineal (mx + b)
    private static double slopeOf(Node arg, String variable) {
        Node d = arg.derivar(variable); 
        if (d.depende(variable)) return Double.NaN; 
        return d.eval(0, variable); 
    }

    // Clase central del Árbol AST. Cada nodo guarda su operación y sus hijos
    private static final class Node {
        final String tipo; 
        final double numero; 
        final String nombre; 
        final Node a, b; 

        Node(String tipo, double numero, String nombre, Node a, Node b) { 
            this.tipo = tipo; this.numero = numero; this.nombre = nombre; this.a = a; this.b = b;
        }
        
        boolean esNumero() { return tipo.equals("num"); } 
        
        boolean depende(String v) { // Reviso si la rama tiene a la variable solicitada
            if (tipo.equals("var")) return nombre.equals(v); 
            if (tipo.equals("num")) return false; 
            return (a != null && a.depende(v)) || (b != null && b.depende(v)); 
        }

        double eval(double x, String variable) { // Resuelvo matemáticamente el nodo
            try {
                return switch (tipo) {
                    case "num" -> numero; 
                    case "var" -> nombre.equals(variable) ? x : 1.0; // Si es otra variable distinta, la dejo en 1 para que se grafique
                    case "neg" -> -a.eval(x, variable); 
                    case "+" -> a.eval(x, variable) + b.eval(x, variable); 
                    case "-" -> a.eval(x, variable) - b.eval(x, variable); 
                    case "*" -> a.eval(x, variable) * b.eval(x, variable); 
                    case "/" -> a.eval(x, variable) / b.eval(x, variable); 
                    case "^" -> Math.pow(a.eval(x, variable), b.eval(x, variable)); 
                    case "sin" -> Math.sin(a.eval(x, variable)); 
                    case "cos" -> Math.cos(a.eval(x, variable)); 
                    case "tan" -> Math.tan(a.eval(x, variable)); 
                    case "ln" -> a.eval(x, variable) > 0 ? Math.log(a.eval(x, variable)) : Double.NaN; 
                    case "log" -> a.eval(x, variable) > 0 ? Math.log10(a.eval(x, variable)) : Double.NaN; 
                    case "exp" -> Math.exp(a.eval(x, variable)); 
                    case "sqrt" -> Math.sqrt(a.eval(x, variable)); 
                    case "abs" -> Math.abs(a.eval(x, variable)); 
                    default -> Double.NaN;
                };
            } catch (RuntimeException ex) { return Double.NaN; } 
        }

        boolean haySingularidad(String variable, double lo, double hi) { // Busco divisiones por cero en el rango
            if (tipo.equals("tan") && a != null && cercaDeCero(fn("cos", a), variable, lo, hi)) return true; 
            if (tipo.equals("/") && b != null && b.depende(variable) && cercaDeCero(b, variable, lo, hi)) return true; 
            if (tipo.equals("^") && b != null && b.esNumero() && b.numero < 0
                    && cercaDeCero(a, variable, lo, hi)) return true; 
            return (a != null && a.haySingularidad(variable, lo, hi))
                    || (b != null && b.haySingularidad(variable, lo, hi)); 
        }

        private static boolean cercaDeCero(Node expresion, String variable, double lo, double hi) {
            final int muestras = 4096; 
            double maxAbs = 0; 
            for (int i = 0; i <= muestras; i++) {
                if ((i & 255) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("Cálculo cancelado.");
                }
                double x = lo + (hi - lo) * i / muestras; 
                double y = expresion.eval(x, variable); 
                if (!Double.isFinite(y)) return true; 
                maxAbs = Math.max(maxAbs, Math.abs(y)); 
            }
            if (maxAbs < 1e-12) return true; 
            double anterior = expresion.eval(lo, variable); 
            for (int i = 1; i <= muestras; i++) {
                if ((i & 255) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("Cálculo cancelado.");
                }
                double x = lo + (hi - lo) * i / muestras;
                double actual = expresion.eval(x, variable);
                if (Math.abs(actual) < 1e-8 * maxAbs) return true; 
                if ((anterior < 0 && actual > 0) || (anterior > 0 && actual < 0)) return true; 
                anterior = actual;
            }
            return false; 
        }

        Node derivar(String v) { // Algoritmo maestro de derivación. Asigna reglas dependiendo del tipo de nodo
            if (!this.depende(v)) return num(0); 
            
            Node da = a == null ? null : a.derivar(v); 
            Node db = b == null ? null : b.derivar(v); 
            return switch (tipo) {
                case "num" -> num(0); 
                case "var" -> num(nombre.equals(v) ? 1 : 0); 
                case "neg" -> neg(da); 
                case "+" -> add(da, db); 
                case "-" -> sub(da, db); 
                case "*" -> add(mul(da, b), mul(a, db)); 
                case "/" -> derivarCociente(this, v); 
                case "^" -> !b.depende(v) 
                        ? mul(mul(b, pow(a, sub(b, num(1)))), da) 
                        : mul(pow(a, b), add(mul(db, fn("ln", a)), 
                                a.str().equals(b.str()) ? da : div(mul(b, da), a)));
                case "sin" -> mul(fn("cos", a), da); 
                case "cos" -> neg(mul(fn("sin", a), da)); 
                case "tan" -> div(da, pow(fn("cos", a), num(2))); 
                case "ln" -> div(da, a); 
                case "log" -> div(da, mul(a, num(Math.log(10)))); 
                case "exp" -> mul(fn("exp", a), da); 
                case "sqrt" -> div(da, mul(num(2), fn("sqrt", a))); 
                case "abs" -> div(mul(a, da), fn("abs", a)); 
                default -> throw new IllegalArgumentException("No se puede derivar el elemento " + tipo + ".");
            };
        }

        String str() { return render(0, false, ""); } 
        
        private String render(int parentPrec, boolean right, String parentOp) { // Recrea la ecuación insertando paréntesis solo cuando es necesario
            int p = prec(tipo); 
            String s;
            switch (tipo) {
                case "num" -> s = nombre == null ? formatoNumero(numero) : nombre; 
                case "var" -> s = nombre; 
                case "neg" -> s = "-" + a.render(p, true, "neg"); 
                case "sin" -> s = "sen(" + a.str() + ")"; 
                case "cos" -> s = "cos(" + a.str() + ")"; 
                case "tan" -> s = "tan(" + a.str() + ")"; 
                case "ln" -> s = "ln(" + a.str() + ")"; 
                case "log" -> s = "log(" + a.str() + ")"; 
                case "exp" -> s = "e^(" + a.str() + ")"; 
                case "sqrt" -> s = "√(" + a.str() + ")"; 
                case "abs" -> s = "|" + a.str() + "|"; 
                case "+", "-", "*", "/", "^" -> {
                    String izquierda = a.render(p, false, tipo); 
                    String derecha = b.render(p, true, tipo); 
                    String op = tipo.equals("*") ? separadorMultiplicacion(a, b, izquierda, derecha) 
                            : tipo.equals("/") ? " / "
                            : tipo.equals("^") ? "^" : " " + tipo + " ";
                    s = izquierda + op + derecha; 
                }
                default -> s = "?";
            }
            boolean parens = p < parentPrec; 
            if (!parens && p == parentPrec && right) { 
                if (parentOp.equals("-") && (tipo.equals("+") || tipo.equals("-"))) parens = true; 
                if (parentOp.equals("/") && (tipo.equals("+") || tipo.equals("-") || tipo.equals("*") || tipo.equals("/"))) parens = true; 
                if (parentOp.equals("^") && tipo.equals("^")) parens = true; 
                if (parentOp.equals("*") && tipo.equals("/")) parens = true;
            }
            if (!parens && parentOp.equals("^") && !right && tipo.equals("^")) parens = true;
            return parens ? "(" + s + ")" : s; 
        }

        private static String separadorMultiplicacion(Node izquierda, Node derecha, String textoIzq, String textoDer) {
            if (izquierda.tipo.equals("num") && !Character.isDigit(textoDer.charAt(0))) return ""; 
            if (textoIzq.endsWith(")") && (textoDer.startsWith("(") || textoDer.startsWith("sen(") || textoDer.startsWith("cos(") || textoDer.startsWith("tan(") || textoDer.startsWith("e^(") || textoDer.startsWith("√("))) return ""; 
            if (izquierda.tipo.equals("var") && derecha.tipo.equals("var")) return ""; 
            if (izquierda.tipo.equals("var") && (derecha.tipo.equals("sin") || derecha.tipo.equals("cos") || derecha.tipo.equals("tan") || derecha.tipo.equals("ln") || derecha.tipo.equals("log") || derecha.tipo.equals("exp") || derecha.tipo.equals("sqrt") || derecha.tipo.equals("abs"))) return " ";
            if ((izquierda.tipo.equals("var") || textoIzq.endsWith(")")) && textoDer.startsWith("(")) return "";
            return " · "; 
        }

        private static int prec(String t) { 
            return switch (t) { case "+", "-" -> 1; case "*", "/" -> 2; case "neg" -> 3; case "^" -> 4; default -> 5; };
        }
    }

    private static boolean esPolinomioNodo(Node n, String variable) { // Evalúa si es polinómico sin divisiones extrañas
        if (!n.depende(variable)) return true; 
        return switch (n.tipo) {
            case "num", "var" -> true; 
            case "neg", "+", "-", "*" -> (n.a == null || esPolinomioNodo(n.a, variable))
                    && (n.b == null || esPolinomioNodo(n.b, variable)); 
            case "^" -> n.b != null && n.b.esNumero() && n.b.numero >= 0
                    && Math.abs(n.b.numero - Math.rint(n.b.numero)) < 1e-10
                    && esPolinomioNodo(n.a, variable); 
            default -> false; 
        };
    }

    private record SignedNode(Node node, int sign) {} 
    
    private static void recogerTerminos(Node n, int sign, List<SignedNode> salida) { // Desarma sumas y restas en lista
        if (n.tipo.equals("+")) {
            recogerTerminos(n.a, sign, salida); 
            recogerTerminos(n.b, sign, salida); 
        } else if (n.tipo.equals("-")) {
            recogerTerminos(n.a, sign, salida); 
            recogerTerminos(n.b, -sign, salida); 
        } else if (n.tipo.equals("neg")) {
            recogerTerminos(n.a, -sign, salida); 
        } else salida.add(new SignedNode(n, sign)); 
    }

    private static String formatearConSigno(Node n, int sign) {
        String texto = n.str();
        if (sign > 0) return texto;
        if (n.esNumero() || n.tipo.equals("var") || n.tipo.equals("*")) return "−" + texto;
        return "−(" + texto + ")";
    }

    private static Node derivarCociente(Node cociente, String variable) { // Regla (u'v - uv')/v^2
        Node du = cociente.a.derivar(variable); 
        Node dv = cociente.b.derivar(variable); 
        Node izquierda = mul(du, cociente.b); 
        Node derecha = mul(cociente.a, dv); 
        Node numerador = sub(izquierda, derecha); 
        Node factorizado = factorizarResta(izquierda, derecha, variable); 
        if (factorizado != null) numerador = factorizado; 
        return div(numerador, pow(cociente.b, num(2))); 
    }

    private static final class Producto { 
        double coeficiente = 1.0;
        final LinkedHashMap<String, FactorPotencia> factores = new LinkedHashMap<>();
    }
    private record FactorPotencia(Node base, double exponente) {}

    private static Node factorizarResta(Node izquierda, Node derecha, String variable) { // Factorizador para el numerador del cociente
        String otra = variable.equals("x") ? "y" : "x";
        if (izquierda.depende(otra) || derecha.depende(otra)) return null; 
        Producto a = descomponerProducto(izquierda);
        Producto b = descomponerProducto(derecha);
        if (!Double.isFinite(a.coeficiente) || !Double.isFinite(b.coeficiente)
                || Math.abs(a.coeficiente) < 1e-12 || Math.abs(b.coeficiente) < 1e-12) return null;
        double comunNumerico = mcdNumerico(a.coeficiente, b.coeficiente); 
        LinkedHashMap<String, FactorPotencia> comunes = new LinkedHashMap<>();
        for (Map.Entry<String, FactorPotencia> e : a.factores.entrySet()) {
            FactorPotencia otraPot = b.factores.get(e.getKey());
            if (otraPot != null) {
                double exp = Math.min(e.getValue().exponente, otraPot.exponente); 
                if (exp > 1e-10) comunes.put(e.getKey(), new FactorPotencia(e.getValue().base, exp));
            }
        }
        if (comunes.isEmpty() && Math.abs(comunNumerico - 1.0) < 1e-10) return null;

        Producto restoA = new Producto();
        restoA.coeficiente = a.coeficiente / comunNumerico;
        Producto restoB = new Producto();
        restoB.coeficiente = b.coeficiente / comunNumerico;
        for (Map.Entry<String, FactorPotencia> e : a.factores.entrySet()) {
            double expComun = comunes.containsKey(e.getKey()) ? comunes.get(e.getKey()).exponente : 0;
            double falta = e.getValue().exponente - expComun;
            if (falta > 1e-10) restoA.factores.put(e.getKey(), new FactorPotencia(e.getValue().base, falta));
        }
        for (Map.Entry<String, FactorPotencia> e : b.factores.entrySet()) {
            double expComun = comunes.containsKey(e.getKey()) ? comunes.get(e.getKey()).exponente : 0;
            double falta = e.getValue().exponente - expComun;
            if (falta > 1e-10) restoB.factores.put(e.getKey(), new FactorPotencia(e.getValue().base, falta));
        }
        Node restoIzq = construirProducto(restoA);
        Node restoDer = construirProducto(restoB);
        Node corchete = simplificarRestaPolinomios(restoIzq, restoDer, variable);
        if (corchete == null) corchete = sub(restoIzq, restoDer);

        Producto factorComun = new Producto();
        factorComun.coeficiente = comunNumerico;
        factorComun.factores.putAll(comunes);
        List<Map.Entry<String, FactorPotencia>> ordenados = new ArrayList<>(factorComun.factores.entrySet());
        ordenados.sort((x, y) -> {
            boolean xv = x.getValue().base.tipo.equals("var");
            boolean yv = y.getValue().base.tipo.equals("var");
            if (xv != yv) return xv ? -1 : 1;
            return Integer.compare(x.getValue().base.tipo.equals("^") ? 1 : 0,
                    y.getValue().base.tipo.equals("^") ? 1 : 0);
        });
        factorComun.factores.clear();
        for (Map.Entry<String, FactorPotencia> e : ordenados) factorComun.factores.put(e.getKey(), e.getValue());
        return mul(construirProducto(factorComun), corchete);
    }

    private static Producto descomponerProducto(Node n) {
        Producto p = new Producto();
        agregarFactor(n, p);
        return p;
    }
    
    private static void agregarFactor(Node n, Producto p) {
        if (n.tipo.equals("*")) {
            agregarFactor(n.a, p); agregarFactor(n.b, p); return;
        }
        if (n.tipo.equals("neg")) { p.coeficiente *= -1; agregarFactor(n.a, p); return; }
        if (n.esNumero() && n.nombre == null) { p.coeficiente *= n.numero; return; }
        Node base = n;
        double exponente = 1.0;
        if (n.tipo.equals("^") && n.b.esNumero()) { base = n.a; exponente = n.b.numero; }
        String clave = base.str();
        FactorPotencia previa = p.factores.get(clave);
        p.factores.put(clave, new FactorPotencia(base, exponente + (previa == null ? 0 : previa.exponente)));
    }
    
    private static Node construirProducto(Producto p) {
        List<Node> factores = new ArrayList<>();
        for (FactorPotencia f : p.factores.values()) {
            Node n = Math.abs(f.exponente - 1) < 1e-10 ? f.base : pow(f.base, num(f.exponente));
            factores.add(n);
        }
        Node resto = num(1);
        for (Node f : factores) resto = mul(resto, f);
        return mul(num(p.coeficiente), resto);
    }
    
    private static double mcdNumerico(double a, double b) { 
        double aa = Math.abs(a), bb = Math.abs(b);
        if (Math.abs(aa - Math.rint(aa)) > 1e-9 || Math.abs(bb - Math.rint(bb)) > 1e-9) return 1.0;
        long x = (long)Math.rint(aa), y = (long)Math.rint(bb);
        while (y != 0) { long r = x % y; x = y; y = r; }
        return x <= 1 ? 1.0 : x;
    }

    private static Node simplificarRestaPolinomios(Node a, Node b, String variable) {
        Map<Integer, Double> pa = polinomio(a, variable), pb = polinomio(b, variable);
        if (pa == null || pb == null) return null;
        Map<Integer, Double> resultado = new TreeMap<>(pa);
        pb.forEach((grado, coef) -> resultado.merge(grado, -coef, Double::sum));
        resultado.entrySet().removeIf(e -> Math.abs(e.getValue()) < 1e-9);
        if (resultado.isEmpty()) return num(0);
        double mcd = 0;
        boolean enteros = true;
        for (double c : resultado.values()) {
            if (Math.abs(c - Math.rint(c)) > 1e-8) { enteros = false; break; }
            mcd = mcd(mcd, Math.abs(c));
        }
        if (enteros && mcd > 1) {
            final double divisor = mcd;
            Map<Integer, Double> reducido = new TreeMap<>();
            resultado.forEach((grado, coef) -> reducido.put(grado, coef / divisor));
            return mul(num(divisor), construirPolinomio(reducido, variable));
        }
        return construirPolinomio(resultado, variable);
    }

    // Escudo de memoria: solo expando binomios hasta grado 5 para evitar matar la memoria RAM
    private static Map<Integer, Double> polinomio(Node n, String variable) {
        if (n.esNumero()) return Map.of(0, n.numero);
        if (n.tipo.equals("var")) return n.nombre.equals(variable) ? Map.of(1, 1.0) : null;
        if (n.tipo.equals("neg")) {
            Map<Integer, Double> p = polinomio(n.a, variable); if (p == null) return null;
            Map<Integer, Double> out = new TreeMap<>(); p.forEach((g,c)->out.put(g,-c)); return out;
        }
        if (n.tipo.equals("+") || n.tipo.equals("-")) {
            Map<Integer, Double> a = polinomio(n.a, variable), b = polinomio(n.b, variable);
            if (a == null || b == null) return null;
            Map<Integer, Double> out = new TreeMap<>(a);
            b.forEach((g,c)->out.merge(g, n.tipo.equals("+") ? c : -c, Double::sum));
            return out;
        }
        if (n.tipo.equals("*")) {
            Map<Integer, Double> a = polinomio(n.a, variable), b = polinomio(n.b, variable);
            if (a == null || b == null) return null;
            return multiplicarPolinomios(a, b);
        }
        if (n.tipo.equals("^") && n.b.esNumero() && n.b.numero >= 0 && n.b.numero <= 5
                && Math.abs(n.b.numero-Math.rint(n.b.numero)) < 1e-10) {
            Map<Integer,Double> base=polinomio(n.a, variable); if(base==null)return null;
            Map<Integer,Double> out=Map.of(0,1.0);
            for(int i=0;i<(int)n.b.numero;i++) {
                out = multiplicarPolinomios(out, base);
                if (out == null) return null; // Aborto si generó más de 100 términos
            }
            return out;
        }
        return null;
    }

    private static Map<Integer,Double> multiplicarPolinomios(Map<Integer,Double> a, Map<Integer,Double> b) {
        if (a.size() * b.size() > 100) return null; // Si excede los 100 factores cruzados, aborto para salvar memoria
        Map<Integer,Double> out=new TreeMap<>();
        for(Map.Entry<Integer,Double> x:a.entrySet()) 
            for(Map.Entry<Integer,Double> y:b.entrySet())
                out.merge(x.getKey()+y.getKey(),x.getValue()*y.getValue(),Double::sum);
        return out;
    }

    private static Node construirPolinomio(Map<Integer,Double> p, String variable) {
        Node resultado = null;
        List<Integer> grados = new ArrayList<>(p.keySet());
        grados.sort(java.util.Comparator.reverseOrder());
        for (int grado : grados) {
            double coef = p.get(grado);
            if (Math.abs(coef) < 1e-9) continue;
            Node monomio = grado == 0 ? num(Math.abs(coef))
                    : mul(num(Math.abs(coef)), grado == 1 ? var(variable) : pow(var(variable), num(grado)));
            if (resultado == null) resultado = coef < 0 ? neg(monomio) : monomio;
            else resultado = coef < 0 ? sub(resultado, monomio) : add(resultado, monomio);
        }
        return resultado == null ? num(0) : resultado;
    }
    
    private static double mcd(double a, double b) {
        long x=Math.round(a), y=Math.round(b); while(y!=0){long r=x%y;x=y;y=r;} return x;
    }

    // Simplificaciones al vuelo para construir nodos más limpios
    private static Node num(double d) {
        if (Math.abs(d) < 1e-12) d = 0; 
        if (Double.isFinite(d) && Math.abs(d) < 1e12 && Math.abs(d - Math.rint(d)) < 1e-10) d = Math.rint(d);
        return new Node("num", d, null, null, null);
    }
    private static Node var(String v) { return new Node("var", 0, v, null, null); }
    private static Node add(Node a, Node b) {
        if (a.esNumero() && Math.abs(a.numero) < 1e-12) return b; 
        if (b.esNumero() && Math.abs(b.numero) < 1e-12) return a; 
        if (a.esNumero() && b.esNumero()) return num(a.numero + b.numero); 
        return new Node("+", 0, null, a, b);
    }
    private static Node sub(Node a, Node b) {
        if (b.esNumero() && Math.abs(b.numero) < 1e-12) return a; 
        if (a.esNumero() && b.esNumero()) return num(a.numero - b.numero); 
        if (a.equals(b)) return num(0); 
        return new Node("-", 0, null, a, b);
    }
    private static Node neg(Node a) {
        if (a.esNumero()) return num(-a.numero); 
        if (a.tipo.equals("neg")) return a.a; 
        return new Node("neg", 0, null, a, null);
    }
    private static Node mul(Node a, Node b) {
        if ((a.esNumero() && Math.abs(a.numero) < 1e-12) || (b.esNumero() && Math.abs(b.numero) < 1e-12)) return num(0); 
        if (a.esNumero() && Math.abs(a.numero - 1) < 1e-12) return b; 
        if (b.esNumero() && Math.abs(b.numero - 1) < 1e-12) return a; 
        if (a.esNumero() && Math.abs(a.numero + 1) < 1e-12) return neg(b); 
        if (b.esNumero() && Math.abs(b.numero + 1) < 1e-12) return neg(a); 
        if (a.esNumero() && b.esNumero()) return num(a.numero * b.numero); 
        if (a.tipo.equals("*") && b.tipo.equals("*") && a.a.esNumero() && b.a.esNumero()) {
            return mul(num(a.a.numero * b.a.numero), new Node("*", 0, null, a.b, b.b)); 
        }
        if (a.esNumero() && b.tipo.equals("*") && b.a.esNumero()) return mul(num(a.numero * b.a.numero), b.b);
        if (a.esNumero() && b.tipo.equals("*") && b.b.esNumero()) return mul(num(a.numero * b.b.numero), b.a);
        if (b.esNumero() && a.tipo.equals("*") && a.a.esNumero()) return mul(num(b.numero * a.a.numero), a.b);
        if (b.esNumero() && a.tipo.equals("*") && a.b.esNumero()) return mul(num(b.numero * a.b.numero), a.a);
        if (a.esNumero() && b.tipo.equals("/") && b.b.esNumero()) return div(mul(a, b.a), b.b);
        if (b.esNumero() && a.tipo.equals("/") && a.b.esNumero()) return div(mul(b, a.a), a.b);
        return new Node("*", 0, null, a, b);
    }
    private static Node div(Node a, Node b) {
        if (a.esNumero() && Math.abs(a.numero) < 1e-12) return num(0); 
        if (b.esNumero() && Math.abs(b.numero - 1) < 1e-12) return a; 
        if (b.esNumero() && Math.abs(b.numero + 1) < 1e-12) return neg(a); 
        if (a.esNumero() && b.esNumero() && Math.abs(b.numero) > 1e-12) return num(a.numero / b.numero);
        if (b.esNumero() && Math.abs(b.numero) > 1e-12 && a.tipo.equals("*") && a.a.esNumero()) {
            return mul(num(a.a.numero / b.numero), a.b); 
        }
        if (b.esNumero() && Math.abs(b.numero) > 1e-12 && a.tipo.equals("*") && a.b.esNumero()) {
            return mul(num(a.b.numero / b.numero), a.a);
        }
        return new Node("/", 0, null, a, b);
    }
    private static Node pow(Node a, Node b) {
        if (b.esNumero() && Math.abs(b.numero) < 1e-12) return num(1); 
        if (b.esNumero() && Math.abs(b.numero - 1) < 1e-12) return a; 
        if (a.esNumero() && b.esNumero()) {
            double v = Math.pow(a.numero, b.numero);
            if (Double.isFinite(v)) return num(v);
        }
        return new Node("^", 0, null, a, b);
    }
    private static Node fn(String f, Node a) {
        if (a.esNumero() && a.nombre == null) {
            double x = a.numero;
            double y = switch (f) {
                case "sin" -> Math.sin(x); case "cos" -> Math.cos(x); case "tan" -> Math.tan(x);
                case "ln" -> x > 0 ? Math.log(x) : Double.NaN; case "log" -> x > 0 ? Math.log10(x) : Double.NaN;
                case "exp" -> Math.exp(x); case "sqrt" -> Math.sqrt(x); case "abs" -> Math.abs(x); default -> Double.NaN;
            };
            if (Double.isFinite(y)) return num(y);
        }
        return new Node(f, 0, null, a, null);
    }

    private static String formatoNumero(double d) {
        if (!Double.isFinite(d)) return String.valueOf(d);
        if (Math.abs(d - Math.rint(d)) < 1e-9 && Math.abs(d) < 1e12) return Long.toString((long) Math.rint(d));
        return BigDecimal.valueOf(d).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // El analizador sintáctico: agarra la frase del usuario y la lee de izquierda a derecha
    private static final class Parser {
        private final String s, variable; 
        private int p; 
        Parser(String s, String variable) { this.s = s; this.variable = variable; }
        
        Node parse() {
            Node n = suma(); 
            if (p != s.length()) throw error("Símbolo inesperado «" + s.charAt(p) + "»."); 
            return n;
        }
        private Node suma() {
            Node n = producto(); 
            while (true) {
                if (take('+')) n = add(n, producto()); 
                else if (take('-')) n = sub(n, producto()); 
                else return n;
            }
        }
        private Node producto() {
            Node n = unario(); 
            while (true) {
                if (take('*')) {
                    n = mul(n, unario()); 
                } else if (take('/')) {
                    n = div(n, unario()); 
                } else if (comienzaPrimario()) {
                    n = mul(n, unario()); // Agarro multiplicación pegada sin asteriscos (ej. 2x)
                } else {
                    return n;
                }
            }
        }
        private boolean comienzaPrimario() {
            if (p >= s.length()) return false;
            char c = s.charAt(p);
            return c == '(' || Character.isDigit(c) || c == '.'
                    || Character.isLetter(c) || c == 'π' || c == '√';
        }
        private Node unario() {
            if (take('+')) return unario(); 
            if (take('-')) return neg(unario()); 
            return potencia();
        }
        private Node potencia() {
            Node n = primario(); 
            if (take('^')) n = pow(n, unario()); 
            return n;
        }
        private Node primario() { // Extrae números, letras, constantes y lo que esté en paréntesis
            if (take('(')) {
                Node n = suma();
                if (!take(')')) throw error("Falta el paréntesis de cierre.");
                return n;
            }
            if (p >= s.length()) throw error("La expresión está incompleta.");
            char c = s.charAt(p);
            if (Character.isDigit(c) || c == '.') { // Leo un número completo
                int start = p;
                boolean punto = false;
                while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) {
                    if (s.charAt(p) == '.') { if (punto) throw error("Número decimal inválido."); punto = true; }
                    p++;
                }
                try { return num(Double.parseDouble(s.substring(start, p))); }
                catch (NumberFormatException ex) { throw error("Número inválido."); }
            }
            if (Character.isLetter(c) || c == 'π' || c == '√') { // Si es texto, busco si es función o letra suelta
                String resto = s.substring(p).toLowerCase(Locale.ROOT);
                if (resto.startsWith("pi")) {
                    p += 2;
                    return new Node("num", Math.PI, "pi", null, null);
                }
                String id = null;
                String[] funciones = {"sqrt", "sen", "sin", "cos", "tan", "ln", "log", "exp", "abs"};
                for (String candidata : funciones) {
                    if (resto.startsWith(candidata)) {
                        id = candidata;
                        p += candidata.length();
                        break;
                    }
                }
                if (id != null) {
                    if (id.equals("sen")) id = "sin";
                    if (!take('(')) throw error("Escribe la función " + id + " con paréntesis, por ejemplo " + id + "(" + variable + ").");
                    Node arg = suma();
                    if (!take(')')) throw error("Falta ')' en la función " + id + ".");
                    return fn(id, arg);
                }
                if (c == 'π') {
                    p++;
                    return new Node("num", Math.PI, "pi", null, null);
                }
                if (c == '√') {
                    p++;
                    if (!take('(')) throw error("Escribe la raíz como √(expresión).");
                    Node arg = suma();
                    if (!take(')')) throw error("Falta ')' en la raíz.");
                    return fn("sqrt", arg);
                }
                p++; 
                String idSimple = String.valueOf(Character.toLowerCase(c));
                // Acepto variables x e y simultáneamente para poder graficarlas o tratarlas como constantes
                if (idSimple.equals("x") || idSimple.equals("y")) {
                    return var(idSimple); 
                }
                if (idSimple.equals("e")) return new Node("num", Math.E, "e", null, null);
                throw error("Variable o función desconocida: «" + idSimple + "».");
            }
            throw error("Carácter inesperado «" + c + "».");
        }
        private boolean take(char c) { if (p < s.length() && s.charAt(p) == c) { p++; return true; } return false; } 
        private IllegalArgumentException error(String msg) { return new IllegalArgumentException(msg + " (posición " + (p + 1) + ")"); }
    }
}