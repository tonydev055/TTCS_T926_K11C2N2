package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietgiohang {
    private Long id;
    private Giohang giohang;
    private Sanpham sanpham;
    private int soluong;
    public Chitietgiohang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Giohang getGiohang() { return giohang; }
    public void setGiohang(Giohang giohang) { this.giohang = giohang; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
}
