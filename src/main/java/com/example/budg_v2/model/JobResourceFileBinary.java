package com.example.budg_v2.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model class for job_resource_file table
 * Stores the actual binary content of the file
 * Note: This is different from JobResourceFile which stores file metadata
 */
public class JobResourceFileBinary {

    @SerializedName("id")
    private Integer id;

    @SerializedName("file_id")
    private Integer fileId; // Foreign key to job_resource_filename(ID)

    @SerializedName("file")
    private byte[] file; // Binary content of the file (longblob)

    // Constructors
    public JobResourceFileBinary() {}

    public JobResourceFileBinary(Integer id, Integer fileId, byte[] file) {
        this.id = id;
        this.fileId = fileId;
        this.file = file;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getFileId() {
        return fileId;
    }

    public void setFileId(Integer fileId) {
        this.fileId = fileId;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }
}

