package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietPhieuNhap {

    private Long id;
    private PhieuNhap phieunhap;
    private SanPham sanpham;
    private int soluong;
    private BigDecimal gianhap;
    private BigDecimal thanhtien;

    public ChiTietPhieuNhap() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PhieuNhap getPhieunhap() {
        return phieunhap;
    }

    public void setPhieunhap(PhieuNhap phieunhap) {
        this.phieunhap = phieunhap;
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

    public BigDecimal getGianhap() {
        return gianhap;
    }

    public void setGianhap(BigDecimal gianhap) {
        this.gianhap = gianhap;
    }

    public BigDecimal getThanhtien() {
        return thanhtien;
    }

    public void setThanhtien(BigDecimal thanhtien) {
        this.thanhtien = thanhtien;
    }
}
