package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PatientApiTest {

    @Autowired MockMvc mvc;

    private static final String JSON =
            "{\"name\":\"John Doe\",\"age\":45,\"diagnosis\":\"Flu\",\"admissionDate\":\"2026-10-01\"}";

    @Test
    void crudFlow() throws Exception {
        mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("John Doe"));

        mvc.perform(get("/patients")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diagnosis").value("Flu"));

        mvc.perform(get("/patients/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionDate").value("2026-10-01"));

        mvc.perform(put("/patients/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Doe\",\"age\":46,\"diagnosis\":\"Recovered\",\"admissionDate\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Recovered"))
                .andExpect(jsonPath("$.age").value(46));

        mvc.perform(delete("/patients/1")).andExpect(status().isNoContent());
        mvc.perform(get("/patients/1")).andExpect(status().isNotFound());
        mvc.perform(delete("/patients/1")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPatient() throws Exception {
        mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
