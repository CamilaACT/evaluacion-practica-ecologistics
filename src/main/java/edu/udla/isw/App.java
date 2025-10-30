package edu.udla.isw;

import edu.udla.isw.model.Envio;
import edu.udla.isw.store.EnvioStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

import java.util.ArrayList;
import java.util.List;

public class App extends RouteBuilder {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        // Bind del store en memoria para usarlo como bean
        main.bind("envioStore", new EnvioStore());
        main.configure().addRoutesBuilder(new App());
        main.run(args);
    }

    @Override
    public void configure() {

        // REST base + OpenAPI
        restConfiguration()
            .component("netty-http")
            .port(7760)
            .contextPath("/api")
            .apiContextPath("/api-doc")
            .apiProperty("api.title", "API EcoLogistics")
            .apiProperty("api.version", "1.0.0");

        // Ping
        rest("/ping")
            .get()
                .produces("application/json")
                .to("direct:ping");
        from("direct:ping")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setBody(simple("{\"status\":\"ok\",\"service\":\"EcoLogistics\",\"ts\":\"${date:now:yyyy-MM-dd HH:mm:ss}\"}"));
        
        // ===== REST: /api/envios =====
        rest("/envios").description("Gestión de envíos")
            .get()
                .produces("application/json")
                .to("direct:getAllEnvios")
            .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:createEnvio");

        rest("/envios/{id}")
            .get()
                .produces("application/json")
                .to("direct:getEnvioById");

        // --- Implementación ---
        from("direct:getAllEnvios")
            .bean("envioStore", "findAll")
            .marshal().json()
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        from("direct:getEnvioById")
            .process(e -> {
                String id = e.getMessage().getHeader("id", String.class);
                Envio found = ((EnvioStore) getContext().getRegistry()
                                .lookupByName("envioStore")).findById(id);
                if (found == null) {
                    e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                    e.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    e.getMessage().setBody("{\"error\":\"Envio no encontrado\"}");
                } else {
                    e.getMessage().setBody(found);
                }
            })
            .marshal().json()
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        from("direct:createEnvio")
            // body -> Envio
            .unmarshal().json(Envio.class)
            .bean("envioStore", "save")
            // refrescar archivo output/envios.json (no bloquea al cliente)
            .wireTap("direct:flushJsonFile")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setBody(simple("{\"message\":\"creado\"}"));

        // Generar/actualizar output/envios.json desde memoria
        from("direct:flushJsonFile")
            .bean("envioStore", "findAll")
            .marshal().json()
            .setHeader(Exchange.FILE_NAME, constant("envios.json"))
            .to("file:output?fileExist=Override")
            .log("Archivo output/envios.json actualizado (${bodyAs(String).length()} bytes)");


        // ===== RUTA: CSV -> JSON (archivo) + memoria =====
        // Lee input/*.csv, mueve el CSV original a archived/ con timestamp
        from("file:input?include=.*\\.csv&move=../archived/${file:name.noext}-${date:now:yyyyMMddHHmmss}.csv")
            .routeId("csv-to-json")
            .log("Procesando CSV: ${file:name}")
            // Convierte CSV a List<List<String>>
            .unmarshal().csv()
            // Mapea a List<Envio> (salta cabecera)
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                List<List<String>> rows = exchange.getMessage().getBody(List.class);

                List<Envio> envios = new ArrayList<>();
                boolean first = true;
                for (List<?> row : rows) {
                    if (first) { first = false; continue; } // saltar encabezado
                    // columnas esperadas: id_envio, cliente, direccion, estado
                    String id = safe(row, 0);
                    String cliente = safe(row, 1);
                    String direccion = safe(row, 2);
                    String estado = safe(row, 3);
                    envios.add(new Envio(id, cliente, direccion, estado));
                }
                exchange.getMessage().setBody(envios);
            })
            // Guarda en memoria
            .bean("envioStore", "saveAll")
            .log("Guardados en memoria: ${body.size()} envíos")
            // Para escribir JSON en output usamos el body nuevamente
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                List<Envio> envios = exchange.getMessage().getBody(List.class);
                exchange.getMessage().setBody(envios);
            })
            .marshal().json()
            .setHeader(Exchange.FILE_NAME, simple("${file:name.noext}.json"))
            .to("file:output")
            .log("JSON generado en output/${file:name.noext}.json");

        // ===== Bootstrap: cargar store desde output/envios.json si existe =====
        from("timer:bootstrap?repeatCount=1&delay=200")
            // intenta leer el archivo una vez al arrancar
            .pollEnrich("file:output?fileName=envios.json&noop=true", 1000)
            .choice()
                .when(body().isNotNull())
                    // el JSON es un array de Envio
                    .unmarshal().json(Envio[].class)
                    .process(e -> {
                        Envio[] arr = e.getMessage().getBody(Envio[].class);
                        java.util.List<Envio> list = java.util.Arrays.asList(arr);
                        e.getMessage().setBody(list);
                    })
                    .bean("envioStore", "saveAll")
                    .log("Store inicializado desde output/envios.json con ${body.size()} envíos")
                .otherwise()
                    .log("No existe output/envios.json aún; Store inicia vacío")
            .end();


    }

    private static String safe(List<?> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size() || row.get(idx) == null) return "";
        return String.valueOf(row.get(idx)).trim();
    }
}
