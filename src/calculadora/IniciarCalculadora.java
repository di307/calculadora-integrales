package calculadora; // Paquete principal donde arranca nuestra aplicación

import calculadora.interfaz.VentanaCalculadora; // Importo la ventana principal que diseñamos
import javax.swing.SwingUtilities; // Utilidad para manejar el hilo gráfico de Swing de forma segura
import javax.swing.UIManager; // Gestor para cambiar el tema visual (Look and Feel)
import javax.swing.JOptionPane; // Cuadro de diálogo por si ocurre un error crítico al arrancar

public class IniciarCalculadora { // Clase lanzadora del proyecto

    public static void main(String[] args) { // Punto de entrada principal de Java
        System.setProperty("awt.useSystemAAFontSettings", "on"); // Activo el suavizado de fuentes del sistema operativo
        System.setProperty("swing.aatext", "true"); // Fuerzo a Swing a dibujar todo el texto con antialiasing

        SwingUtilities.invokeLater(() -> { // Ejecuto la interfaz dentro del hilo de eventos de Swing para que no se congele
            try { // Bloque try general para capturar cualquier fallo al abrir la app
                boolean temaAplicado = false; // Bandera para saber si logramos cargar el primer tema oscuro
                try { // Intento aplicar FlatDarkLaf primero
                    UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf"); // Cargo la librería FlatLaf si está instalada
                    temaAplicado = true; // Marco que el tema sí se pudo aplicar
                } catch (Exception flatLafNoDisponible) { // Si no tengo la librería en el proyecto, ignoro el error y sigo
                }
                if (!temaAplicado) { // Si no cargó FlatLaf, busco el tema Nimbus que ya viene con Java
                    for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) { // Recorro los temas instalados en mi JDK
                        if ("Nimbus".equals(info.getName())) { // Verifico si el tema actual de la lista es Nimbus
                            try { // Intento activar Nimbus
                                UIManager.setLookAndFeel(info.getClassName()); // Aplico la clase de Nimbus al gestor visual
                            } catch (Exception nimbusNoDisponible) { // Si falla, dejo que Java use el tema por defecto
                            }
                            break; // Salgo del ciclo porque ya encontré Nimbus
                        }
                    }
                }

                VentanaCalculadora ventana = new VentanaCalculadora(); // Creo la instancia de mi ventana principal
                ventana.setVisible(true); // Hago visible la calculadora en pantalla

            } catch (Exception e) { // Si algo grave falla al iniciar, atrapo la excepción aquí
                JOptionPane.showMessageDialog(null, // Muestro una alerta con el detalle del error
                        "Error al iniciar la calculadora:\n" + e.getMessage(),
                        "Error Crítico", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}