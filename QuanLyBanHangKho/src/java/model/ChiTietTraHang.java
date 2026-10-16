package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietTraHang {

    private Long id;
    private TraHang trahang;
    private SanPham sanpham;
    private int soluong;
    private BigDecimal dongia;
    private BigDecimal thanhtien;

    public ChiTietTraHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TraHang getTrahang() {
        return trahang;
    }

    public void setTrahang(TraHang trahang) {
        this.trahang = trahang;
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
