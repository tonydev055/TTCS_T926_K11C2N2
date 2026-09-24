package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitiettrahang {
    private Long id;
    private Trahang trahang;
    private Sanpham sanpham;
    private int soluong;
    private BigDecimal dongia;
    private BigDecimal thanhtien;
    public Chitiettrahang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Trahang getTrahang() { return trahang; }
    public void setTrahang(Trahang trahang) { this.trahang = trahang; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
    public BigDecimal getDongia() { return dongia; }
    public void setDongia(BigDecimal dongia) { this.dongia = dongia; }
    public BigDecimal getThanhtien() { return thanhtien; }
    public void setThanhtien(BigDecimal thanhtien) { this.thanhtien = thanhtien; }
}
