package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietphieunhap {
    private Long id;
    private Phieunhap phieunhap;
    private Sanpham sanpham;
    private int soluong;
    private BigDecimal gianhap;
    private BigDecimal thanhtien;
    public Chitietphieunhap() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Phieunhap getPhieunhap() { return phieunhap; }
    public void setPhieunhap(Phieunhap phieunhap) { this.phieunhap = phieunhap; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
    public BigDecimal getGianhap() { return gianhap; }
    public void setGianhap(BigDecimal gianhap) { this.gianhap = gianhap; }
    public BigDecimal getThanhtien() { return thanhtien; }
    public void setThanhtien(BigDecimal thanhtien) { this.thanhtien = thanhtien; }
}
