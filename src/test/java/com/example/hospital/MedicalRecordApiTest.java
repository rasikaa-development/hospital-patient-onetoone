package com.example.hospital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

// Fresh context afterwards so PatientApiTest (which assumes patient id 1) is unaffected.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class MedicalRecordApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String PATIENT = "{\"name\":\"Jane Roe\",\"age\":30,\"diagnosis\":\"Asthma\"}";
    private static final String PATIENT_WITH_RECORD =
            "{\"name\":\"Jane Roe\",\"age\":30,\"medicalRecord\":"
                    + "{\"bloodGroup\":\"O+\",\"allergies\":\"Penicillin\",\"emergencyContact\":\"555-0100\"}}";
    private static final String RECORD = "{\"bloodGroup\":\"A-\",\"allergies\":\"None\",\"emergencyContact\":\"555-0199\"}";

    private long createPatient(String json) throws Exception {
        String body = mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int recordRows(long patientId) {
        return jdbc.queryForObject("select count(*) from medical_record where patient_id = ?",
                Integer.class, patientId);
    }

    @Test
    void recordIsCreatedTogetherWithPatient() throws Exception {
        long id = createPatient(PATIENT_WITH_RECORD);

        mvc.perform(get("/patients/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalRecord.id").exists())
                .andExpect(jsonPath("$.medicalRecord.bloodGroup").value("O+"))
                .andExpect(jsonPath("$.medicalRecord.patient").doesNotExist());
        mvc.perform(get("/patients/" + id + "/medical-record")).andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies").value("Penicillin"));
        assertEquals(1, recordRows(id));
    }

    @Test
    void putCreatesThenUpdatesTheSameRecord() throws Exception {
        long id = createPatient(PATIENT);
        mvc.perform(get("/patients/" + id + "/medical-record")).andExpect(status().isNotFound());

        String created = mvc.perform(put("/patients/" + id + "/medical-record")
                        .contentType(MediaType.APPLICATION_JSON).content(RECORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(put("/patients/" + id + "/medical-record").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bloodGroup\":\"B+\",\"allergies\":\"Latex\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(((Number) JsonPath.read(created, "$.id")).intValue()))
                .andExpect(jsonPath("$.bloodGroup").value("B+"));
        assertEquals(1, recordRows(id));
    }

    @Test
    void deletingRecordKeepsPatient() throws Exception {
        long id = createPatient(PATIENT_WITH_RECORD);

        mvc.perform(delete("/patients/" + id + "/medical-record")).andExpect(status().isNoContent());
        mvc.perform(get("/patients/" + id + "/medical-record")).andExpect(status().isNotFound());
        mvc.perform(get("/patients/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalRecord").doesNotExist());
        assertEquals(0, recordRows(id));
        mvc.perform(delete("/patients/" + id + "/medical-record")).andExpect(status().isNotFound());
    }

    @Test
    void deletingPatientCascadesToRecord() throws Exception {
        long id = createPatient(PATIENT_WITH_RECORD);
        assertEquals(1, recordRows(id));

        mvc.perform(delete("/patients/" + id)).andExpect(status().isNoContent());
        assertEquals(0, recordRows(id));
    }

    @Test
    void patientUpdateLeavesRecordUntouched() throws Exception {
        long id = createPatient(PATIENT_WITH_RECORD);

        mvc.perform(put("/patients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Roe\",\"age\":31,\"diagnosis\":\"Recovered\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/patients/" + id + "/medical-record")).andExpect(status().isOk())
                .andExpect(jsonPath("$.bloodGroup").value("O+"));
    }

    @Test
    void validatesRecordAndUnknownPatient() throws Exception {
        mvc.perform(post("/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"age\":1,\"medicalRecord\":{\"bloodGroup\":\"\"}}"))
                .andExpect(status().isBadRequest());

        long id = createPatient(PATIENT);
        mvc.perform(put("/patients/" + id + "/medical-record").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bloodGroup\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/patients/999999/medical-record").contentType(MediaType.APPLICATION_JSON)
                        .content(RECORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsSecondRecordForSamePatient() throws Exception {
        long id = createPatient(PATIENT_WITH_RECORD);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into medical_record (blood_group, patient_id) values ('AB+', ?)", id));
    }
}
