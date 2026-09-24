package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietphieuxuat {
    private Long id;
    private Phieuxuat phieuxuat;
    private Sanpham sanpham;
    private Lohang lohang;
    private int soluong;
    public Chitietphieuxuat() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Phieuxuat getPhieuxuat() { return phieuxuat; }
    public void setPhieuxuat(Phieuxuat phieuxuat) { this.phieuxuat = phieuxuat; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public Lohang getLohang() { return lohang; }
    public void setLohang(Lohang lohang) { this.lohang = lohang; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
}
