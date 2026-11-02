package com.example.hospital;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The patient's single medical record, exposed as a sub-resource of the patient. */
@RestController
@RequestMapping("/patients/{patientId}/medical-record")
public class MedicalRecordController {

    private final PatientRepository repository;

    public MedicalRecordController(PatientRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<MedicalRecord> get(@PathVariable Long patientId) {
        // 404 both when the patient is missing and when the patient has no record yet
        return repository.findById(patientId)
                .map(Patient::getMedicalRecord)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Create the record (201) or update it in place (200). */
    @PutMapping
    @Transactional
    public ResponseEntity<MedicalRecord> upsert(@PathVariable Long patientId,
                                                @Valid @RequestBody MedicalRecord body) {
        return repository.findById(patientId).map(patient -> {
            MedicalRecord record = patient.getMedicalRecord();
            boolean created = record == null;
            if (created) {
                record = new MedicalRecord();
                patient.setMedicalRecord(record);
            }
            // Update in place: replacing the row would insert the new one before deleting
            // the old one and trip the UNIQUE(patient_id) constraint.
            record.setBloodGroup(body.getBloodGroup());
            record.setAllergies(body.getAllergies());
            record.setEmergencyContact(body.getEmergencyContact());
            repository.flush(); // cascades the insert so the id is populated in the response
            return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(record);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long patientId) {
        return repository.findById(patientId)
                .filter(patient -> patient.getMedicalRecord() != null)
                .map(patient -> {
                    patient.setMedicalRecord(null); // orphanRemoval deletes the row
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
