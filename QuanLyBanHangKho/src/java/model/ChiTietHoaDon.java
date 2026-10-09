package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietHoaDon {

    private Long id;
    private HoaDon hoadon;
    private SanPham sanpham;
    private int soluong;
    private BigDecimal giavon;
    private BigDecimal dongia;
    private BigDecimal thanhtien;

    public ChiTietHoaDon() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public HoaDon getHoadon() {
        return hoadon;
    }

    public void setHoadon(HoaDon hoadon) {
        this.hoadon = hoadon;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public int getSoluong() {
        return soluong;
    }

    public void setSoluong(int soluong) {
        this.soluong = soluong;
    }

    public BigDecimal getGiavon() {
        return giavon;
    }

    public void setGiavon(BigDecimal giavon) {
        this.giavon = giavon;
    }

    public BigDecimal getDongia() {
        return dongia;
    }

    public void setDongia(BigDecimal dongia) {
        this.dongia = dongia;
    }

    public BigDecimal getThanhtien() {
        return thanhtien;
    }

    public void setThanhtien(BigDecimal thanhtien) {
        this.thanhtien = thanhtien;
    }
}
