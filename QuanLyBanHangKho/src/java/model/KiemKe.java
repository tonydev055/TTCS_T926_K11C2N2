package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class KiemKe {

    private Long id;
    private String makiemke;
    private Kho kho;
    private String trangthai;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String ghichu;

    public KiemKe() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMakiemke() {
        return makiemke;
    }

    public void setMakiemke(String makiemke) {
        this.makiemke = makiemke;
    }

    public Kho getKho() {
        return kho;
    }

    public void setKho(Kho kho) {
        this.kho = kho;
    }

    public String getTrangthai() {
        return trangthai;
    }

    public void setTrangthai(String trangthai) {
        this.trangthai = trangthai;
    }

    public LocalDateTime getThoigian() {
        return thoigian;
    }

    public void setThoigian(LocalDateTime thoigian) {
        this.thoigian = thoigian;
    }

    public String getGhichu() {
        return ghichu;
    }

    public void setGhichu(String ghichu) {
        this.ghichu = ghichu;
    }
}
