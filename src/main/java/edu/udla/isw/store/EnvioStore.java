package edu.udla.isw.store;

import edu.udla.isw.model.Envio;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnvioStore {
    // indice por id
    private final Map<String, Envio> data = new ConcurrentHashMap<>();

    public synchronized void saveAll(List<Envio> envios) {
        for (Envio e : envios) {
            if (e.getId() != null && !e.getId().isBlank()) {
                data.put(e.getId(), e);
            }
        }
    }

    public synchronized List<Envio> findAll() {
        return new ArrayList<>(data.values());
    }

    public synchronized Envio findById(String id) {
        return data.get(id);
    }

    public synchronized void save(Envio e) {
        if (e.getId() == null || e.getId().isBlank()) {
            throw new IllegalArgumentException("id requerido");
        }
        data.put(e.getId(), e);
    }

    public synchronized void clear() {
        data.clear();
    }
}
