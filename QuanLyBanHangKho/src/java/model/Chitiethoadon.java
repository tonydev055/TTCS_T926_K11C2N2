package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitiethoadon {
    private Long id;
    private Hoadon hoadon;
    private Sanpham sanpham;
    private int soluong;
    private BigDecimal giavon;
    private BigDecimal dongia;
    private BigDecimal thanhtien;
    public Chitiethoadon() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Hoadon getHoadon() { return hoadon; }
    public void setHoadon(Hoadon hoadon) { this.hoadon = hoadon; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
    public BigDecimal getGiavon() { return giavon; }
    public void setGiavon(BigDecimal giavon) { this.giavon = giavon; }
    public BigDecimal getDongia() { return dongia; }
    public void setDongia(BigDecimal dongia) { this.dongia = dongia; }
    public BigDecimal getThanhtien() { return thanhtien; }
    public void setThanhtien(BigDecimal thanhtien) { this.thanhtien = thanhtien; }
}
