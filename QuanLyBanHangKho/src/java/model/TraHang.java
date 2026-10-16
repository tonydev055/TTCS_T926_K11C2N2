package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class TraHang {

    private Long id;
    private String maphieutra;
    private HoaDon hoadon;
    private Kho kho;
    private String trangthai;
    private String lydo;
    private BigDecimal tongtienhoan = BigDecimal.ZERO;
    private LocalDateTime thoigian = LocalDateTime.now();

    public TraHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaphieutra() {
        return maphieutra;
    }

    public void setMaphieutra(String maphieutra) {
        this.maphieutra = maphieutra;
    }

    public HoaDon getHoadon() {
        return hoadon;
    }

    public void setHoadon(HoaDon hoadon) {
        this.hoadon = hoadon;
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

    public String getLydo() {
        return lydo;
    }

    public void setLydo(String lydo) {
        this.lydo = lydo;
    }

    public BigDecimal getTongtienhoan() {
        return tongtienhoan;
    }

    public void setTongtienhoan(BigDecimal tongtienhoan) {
        this.tongtienhoan = tongtienhoan;
    }

    public LocalDateTime getThoigian() {
        return thoigian;
    }

    public void setThoigian(LocalDateTime thoigian) {
        this.thoigian = thoigian;
    }
}
