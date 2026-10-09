package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class PhieuNhap {

    private Long id;
    private String maphieunhap;
    private Kho kho;
    private NhaCungCap nhacungcap;
    private LocalDateTime thoigian = LocalDateTime.now();
    private BigDecimal tongtien = BigDecimal.ZERO;
    private String ghichu;

    public PhieuNhap() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaphieunhap() {
        return maphieunhap;
    }

    public void setMaphieunhap(String maphieunhap) {
        this.maphieunhap = maphieunhap;
    }

    public Kho getKho() {
        return kho;
    }

    public void setKho(Kho kho) {
        this.kho = kho;
    }

    public NhaCungCap getNhacungcap() {
        return nhacungcap;
    }

    public void setNhacungcap(NhaCungCap nhacungcap) {
        this.nhacungcap = nhacungcap;
    }

    public LocalDateTime getThoigian() {
        return thoigian;
    }

    public void setThoigian(LocalDateTime thoigian) {
        this.thoigian = thoigian;
    }

    public BigDecimal getTongtien() {
        return tongtien;
    }

    public void setTongtien(BigDecimal tongtien) {
        this.tongtien = tongtien;
    }

    public String getGhichu() {
        return ghichu;
    }

    public void setGhichu(String ghichu) {
        this.ghichu = ghichu;
    }
}
