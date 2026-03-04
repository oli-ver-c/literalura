package com.alura.literalura.principal;

import com.alura.literalura.dto.DatosLibro;
import com.alura.literalura.dto.DatosAutor;
import com.alura.literalura.model.*;
import com.alura.literalura.repository.AutorRepository;
import com.alura.literalura.repository.LibroRepository;
import com.alura.literalura.service.ConsumoAPI;
import com.alura.literalura.service.ConvierteDatos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class Principal {
    private Scanner teclado = new Scanner(System.in);
    private ConsumoAPI consumoAPI = new ConsumoAPI();
    private ConvierteDatos conversor = new ConvierteDatos();
    private final String URL_BASE = "https://gutendex.com/books/";
    private final String OPEN_LIBRARY_URL = "https://openlibrary.org/search.json?q=";
    private final String GOOGLE_BOOKS_URL = "https://www.googleapis.com/books/v1/volumes?q=intitle:";
    
    @Autowired
    private LibroRepository libroRepository;
    
    @Autowired
    private AutorRepository autorRepository;

    public void mostrarMenu() {
        var opcion = -1;
        while (opcion != 0) {
            var menu = """
                    
                    ===================================
                    LITERALURA - CATÁLOGO DE LIBROS
                    ===================================
                    
                    1 - Buscar libro por título
                    2 - Listar libros registrados
                    3 - Listar autores registrados
                    4 - Listar autores vivos en un año
                    5 - Listar libros por idioma
                    6 - Top 10 libros más descargados
                    7 - Buscar autor por nombre
                    0 - Salir
                    
                    Elija una opción: 
                    """;
            System.out.print(menu);

            try {
                opcion = teclado.nextInt();
                teclado.nextLine();

                switch (opcion) {
                    case 1:
                        buscarLibroPorTitulo();
                        break;
                    case 2:
                        listarLibrosRegistrados();
                        break;
                    case 3:
                        listarAutoresRegistrados();
                        break;
                    case 4:
                        listarAutoresVivosEnYear();
                        break;
                    case 5:
                        listarLibrosPorIdioma();
                        break;
                    case 6:
                        top10Libros();
                        break;
                    case 7:
                        buscarAutorPorNombre();
                        break;
                    case 0:
                        System.out.println("¡Gracias por usar LiterAlura! Hasta luego.");
                        break;
                    default:
                        System.out.println("Opción no válida. Por favor, intente de nuevo.");
                }
            } catch (InputMismatchException e) {
                System.out.println("Error: Debe ingresar un número válido.");
                teclado.nextLine();
            }
        }
    }

    private void buscarLibroPorTitulo() {
        System.out.print("Ingrese el título del libro que desea buscar: ");
        var tituloLibro = teclado.nextLine();
        
        // Verificar si ya existe en BD
        Optional<Libro> libroExistente = libroRepository.findByTituloContainingIgnoreCase(tituloLibro);
        if (libroExistente.isPresent()) {
            System.out.println("\nEl libro ya está registrado en la base de datos:");
            System.out.println(libroExistente.get());
            return;
        }
        
        // Intentar con GUTENDEX primero
        System.out.println("\n🔍 Buscando en Gutendex...");
        if (buscarEnGutendex(tituloLibro)) {
            return;
        }
        
        // Si no encuentra, intentar con OPEN LIBRARY
        System.out.println("\n🔍 Buscando en Open Library...");
        if (buscarEnOpenLibrary(tituloLibro)) {
            return;
        }
        
        // Si no encuentra, intentar con GOOGLE BOOKS
        System.out.println("\n🔍 Buscando en Google Books...");
        if (buscarEnGoogleBooks(tituloLibro)) {
            return;
        }
        
        // Si ninguna API encuentra el libro
        System.out.println("\n❌ El libro no fue encontrado en ninguna API.");
    }

    private boolean buscarEnGutendex(String titulo) {
        try {
            String url = URL_BASE + "?search=" + titulo.replace(" ", "%20");
            var json = consumoAPI.obtenerDatos(url);
            
            var jsonResponse = conversor.obtenerDatos(json, Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) jsonResponse.get("results");
            
            if (results != null && !results.isEmpty()) {
                Map<String, Object> primerLibro = results.get(0);
                
                ObjectMapper mapper = new ObjectMapper();
                String libroJson = mapper.writeValueAsString(primerLibro);
                DatosLibro datosLibro = conversor.obtenerDatos(libroJson, DatosLibro.class);
                
                Optional<Libro> libroExacto = libroRepository.findByTituloContainingIgnoreCase(datosLibro.titulo());
                
                if (libroExacto.isPresent()) {
                    System.out.println("\nEl libro ya está registrado en la base de datos:");
                    System.out.println(libroExacto.get());
                } else {
                    Libro nuevoLibro = new Libro(datosLibro);
                    libroRepository.save(nuevoLibro);
                    System.out.println("\n✅ ¡Libro registrado con éxito desde Gutendex!");
                    System.out.println(nuevoLibro);
                }
                return true;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error en Gutendex: " + e.getMessage());
        }
        return false;
    }

    private boolean buscarEnOpenLibrary(String titulo) {
        try {
            String url = OPEN_LIBRARY_URL + titulo.replace(" ", "+");
            var json = consumoAPI.obtenerDatos(url);
            var jsonResponse = conversor.obtenerDatos(json, Map.class);
            List<Map<String, Object>> docs = (List<Map<String, Object>>) jsonResponse.get("docs");
            
            if (docs != null && !docs.isEmpty()) {
                Map<String, Object> libro = docs.get(0);
                String tituloEncontrado = (String) libro.get("title");
                List<String> autores = (List<String>) libro.get("author_name");
                List<String> idiomas = (List<String>) libro.get("language");
                
                String idioma = (idiomas != null && !idiomas.isEmpty()) ? idiomas.get(0) : "es";
                
                if (autores != null && !autores.isEmpty()) {
                    System.out.println("\n📖 Libro encontrado en Open Library:");
                    System.out.println("   Título: " + tituloEncontrado);
                    System.out.println("   Autor: " + autores.get(0));
                    System.out.println("   Idioma: " + idioma);
                    
                    System.out.print("\n¿Desea guardar este libro? (s/n): ");
                    String respuesta = teclado.nextLine().trim().toLowerCase();
                    
                    if (respuesta.equals("s")) {
                        guardarLibroManual(tituloEncontrado, autores.get(0), idioma);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error en Open Library: " + e.getMessage());
        }
        return false;
    }

    private boolean buscarEnGoogleBooks(String titulo) {
        try {
            String url = GOOGLE_BOOKS_URL + titulo.replace(" ", "+") + "&maxResults=1";
            var json = consumoAPI.obtenerDatos(url);
            var jsonResponse = conversor.obtenerDatos(json, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) jsonResponse.get("items");
            
            if (items != null && !items.isEmpty()) {
                Map<String, Object> primerItem = items.get(0);
                Map<String, Object> volumeInfo = (Map<String, Object>) primerItem.get("volumeInfo");
                
                String tituloEncontrado = (String) volumeInfo.get("title");
                List<String> autores = (List<String>) volumeInfo.get("authors");
                String idioma = (String) volumeInfo.get("language");
                
                if (autores != null && !autores.isEmpty()) {
                    System.out.println("\n📖 Libro encontrado en Google Books:");
                    System.out.println("   Título: " + tituloEncontrado);
                    System.out.println("   Autor: " + autores.get(0));
                    System.out.println("   Idioma: " + (idioma != null ? idioma : "desconocido"));
                    
                    System.out.print("\n¿Desea guardar este libro? (s/n): ");
                    String respuesta = teclado.nextLine().trim().toLowerCase();
                    
                    if (respuesta.equals("s")) {
                        guardarLibroManual(tituloEncontrado, autores.get(0), 
                                           idioma != null ? idioma : "en");
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error en Google Books: " + e.getMessage());
        }
        return false;
    }

private void guardarLibroManual(String titulo, String autorNombre, String idioma) {
    try {
        // Buscar o crear el autor
        Optional<Autor> autorExistente = autorRepository.findByNombreContainingIgnoreCase(autorNombre);
        Autor autor;
        
        if (autorExistente.isPresent()) {
            autor = autorExistente.get();
            System.out.println("📝 Autor existente encontrado: " + autor.getNombre());
        } else {
            autor = new Autor();
            autor.setNombre(autorNombre);
            autor = autorRepository.save(autor);
            System.out.println("✅ Nuevo autor creado: " + autor.getNombre());
        }
        
        // Verificar si el libro ya existe
        Optional<Libro> libroExistente = libroRepository.findByTituloContainingIgnoreCase(titulo);
        if (libroExistente.isPresent()) {
            System.out.println("\n⚠️ El libro ya existe en la base de datos:");
            System.out.println(libroExistente.get());
            return;
        }
        
        // Crear nuevo libro (SIN asignar el autor aún)
        Libro libro = new Libro();
        libro.setTitulo(titulo);
        libro.setIdioma(idioma);
        libro.setNumeroDescargas(0.0);
        
        // Guardar el libro primero
        libro = libroRepository.save(libro);
        
        // Ahora asignar el autor y guardar la relación
        // Necesitamos una lista mutable
        List<Autor> autores = new ArrayList<>();
        autores.add(autor);
        libro.setAutores(autores);
        
        // Guardar de nuevo para actualizar la relación
        libro = libroRepository.save(libro);
        
        System.out.println("\n✅ ¡Libro guardado exitosamente!");
        System.out.println("   📖 Título: " + libro.getTitulo());
        System.out.println("   ✍️ Autor: " + libro.getAutores().get(0).getNombre());
        System.out.println("   🌐 Idioma: " + libro.getIdioma());
        System.out.println("   📊 Descargas: " + libro.getNumeroDescargas());
        
    } catch (Exception e) {
        System.out.println("❌ Error al guardar: " + e.getMessage());
        e.printStackTrace();
    }
}
    private void listarLibrosRegistrados() {
        List<Libro> libros = libroRepository.findAll();

        if (libros.isEmpty()) {
            System.out.println("\nNo hay libros registrados en la base de datos.");
        } else {
            System.out.println("\n===== LIBROS REGISTRADOS =====");
            libros.forEach(System.out::println);
        }
    }

    private void listarAutoresRegistrados() {
        List<Autor> autores = autorRepository.findAllOrderByFechaNacimiento();

        if (autores.isEmpty()) {
            System.out.println("\nNo hay autores registrados en la base de datos.");
        } else {
            System.out.println("\n===== AUTORES REGISTRADOS =====");
            autores.forEach(System.out::println);
        }
    }

    private void listarAutoresVivosEnYear() {
        System.out.print("Ingrese el año para buscar autores vivos: ");

        try {
            int year = teclado.nextInt();
            teclado.nextLine();

            List<Autor> autoresVivos = autorRepository.findAutoresVivosEnYear(year);

            if (autoresVivos.isEmpty()) {
                System.out.println("\nNo se encontraron autores vivos en el año " + year + ".");
            } else {
                System.out.println("\n===== AUTORES VIVOS EN " + year + " =====");
                autoresVivos.forEach(System.out::println);
            }
        } catch (InputMismatchException e) {
            System.out.println("Error: Debe ingresar un año válido.");
            teclado.nextLine();
        }
    }

    private void listarLibrosPorIdioma() {
        var menuIdiomas = """
                \nSeleccione el idioma:
                es - Español
                en - Inglés
                fr - Francés
                pt - Portugués
                
                Ingrese la opción (es/en/fr/pt): 
                """;
        System.out.print(menuIdiomas);

        var idioma = teclado.nextLine().toLowerCase();

        List<String> idiomasValidos = Arrays.asList("es", "en", "fr", "pt");

        if (!idiomasValidos.contains(idioma)) {
            System.out.println("Idioma no válido. Por favor, seleccione entre: es, en, fr, pt");
            return;
        }

        List<Libro> librosPorIdioma = libroRepository.findByIdiomaIgnoreCase(idioma);

        if (librosPorIdioma.isEmpty()) {
            System.out.println("\nNo se encontraron libros en " + obtenerNombreIdioma(idioma) + ".");
        } else {
            System.out.println("\n===== LIBROS EN " + obtenerNombreIdioma(idioma).toUpperCase() + " =====");
            librosPorIdioma.forEach(System.out::println);
        }
    }

    private String obtenerNombreIdioma(String codigo) {
        return switch (codigo) {
            case "es" -> "Español";
            case "en" -> "Inglés";
            case "fr" -> "Francés";
            case "pt" -> "Portugués";
            default -> "Desconocido";
        };
    }

    private void top10Libros() {
        List<Libro> topLibros = libroRepository.findTop10ByOrderByNumeroDescargasDesc();

        if (topLibros.isEmpty()) {
            System.out.println("\nNo hay libros registrados en la base de datos.");
        } else {
            System.out.println("\n===== TOP 10 LIBROS MÁS DESCARGADOS =====");
            for (int i = 0; i < topLibros.size(); i++) {
                System.out.println((i + 1) + ". " + topLibros.get(i).getTitulo() +
                        " - Descargas: " + topLibros.get(i).getNumeroDescargas());
            }
        }
    }

    private void buscarAutorPorNombre() {
        System.out.print("Ingrese el nombre del autor que desea buscar: ");
        var nombreAutor = teclado.nextLine();

        Optional<Autor> autor = autorRepository.findByNombreContainingIgnoreCase(nombreAutor);

        if (autor.isPresent()) {
            System.out.println("\nAutor encontrado:");
            System.out.println(autor.get());

            System.out.println("\nLibros de este autor:");
            autor.get().getLibros().forEach(libro ->
                    System.out.println("- " + libro.getTitulo()));
        } else {
            System.out.println("\nNo se encontró ningún autor con ese nombre.");
        }
    }
}