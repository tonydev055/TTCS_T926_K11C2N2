package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietDonHang {

    private Long id;
    private DonHang donhang;
    private SanPham sanpham;
    private int soluong;
    private BigDecimal giavon;
    private BigDecimal giagoc;
    private BigDecimal dongia;
    private BigDecimal giasan = BigDecimal.ZERO;
    private BigDecimal chietkhau = BigDecimal.ZERO;
    private BigDecimal thanhtien;

    public ChiTietDonHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DonHang getDonhang() {
        return donhang;
    }

    public void setDonhang(DonHang donhang) {
        this.donhang = donhang;
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

    public BigDecimal getGiagoc() {
        return giagoc;
    }

    public void setGiagoc(BigDecimal giagoc) {
        this.giagoc = giagoc;
    }

    public BigDecimal getDongia() {
        return dongia;
    }

    public void setDongia(BigDecimal dongia) {
        this.dongia = dongia;
    }

    public BigDecimal getGiasan() {
        return giasan;
    }

    public void setGiasan(BigDecimal giasan) {
        this.giasan = giasan;
    }

    public BigDecimal getChietkhau() {
        return chietkhau;
    }

    public void setChietkhau(BigDecimal chietkhau) {
        this.chietkhau = chietkhau;
    }

    public BigDecimal getThanhtien() {
        return thanhtien;
    }

    public void setThanhtien(BigDecimal thanhtien) {
        this.thanhtien = thanhtien;
    }
}
