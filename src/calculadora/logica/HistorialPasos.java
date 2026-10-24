package calculadora.logica; // Paquete donde guardo toda la lógica matemática

import java.util.ArrayList; // Lista dinámica para ir guardando cada paso
import java.util.List; // Interfaz base para manejar listas

public class HistorialPasos { // Clase que empaqueta el resultado final y los pasos explicados

    // Record que guarda el número de paso, el título de la regla, la explicación y la operación
    public record PasoExplicado(int numero, String reglaAplicada, String teorema, String operacion) {}

    private final String expresionOriginal; // Guardo la ecuación tal como se procesó
    private final String expresionResultado; // Guardo el resultado final ya resuelto
    private final double pendienteEnUno; // Guardo el valor de la derivada evaluada en 1
    private final List<PasoExplicado> pasos = new ArrayList<>(); // Lista donde se acumulan los pasos en orden

    // Constructor que recibe los datos principales al terminar un cálculo
    public HistorialPasos(String expresionOriginal, String expresionResultado, double pendienteEnUno) {
        this.expresionOriginal = expresionOriginal; // Asigno la función original
        this.expresionResultado = expresionResultado; // Asigno el texto del resultado final
        this.pendienteEnUno = pendienteEnUno; // Asigno el valor numérico de la pendiente en 1
    }

    // Método para meter un nuevo paso a la lista numerándolo automáticamente
    public void agregarPaso(String reglaAplicada, String teorema, String operacion) {
        pasos.add(new PasoExplicado(pasos.size() + 1, reglaAplicada, teorema, operacion)); // Calculo el número con size() + 1
    }

    public String getExpresionOriginal() { return expresionOriginal; } // Devuelve la función original
    public String getExpresionResultado() { return expresionResultado; } // Devuelve el resultado final para el KPI
    public double getPendienteEnUno() { return pendienteEnUno; } // Devuelve la pendiente evaluada en 1
    public List<PasoExplicado> getPasos() { return List.copyOf(pasos); } // Devuelve una copia protegida de la lista de pasos
}