package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietkiemke {
    private Long id;
    private Kiemke kiemke;
    private Sanpham sanpham;
    private int soluonghethong;
    private int soluongthucte;
    private int chenhlech;
    public Chitietkiemke() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kiemke getKiemke() { return kiemke; }
    public void setKiemke(Kiemke kiemke) { this.kiemke = kiemke; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluonghethong() { return soluonghethong; }
    public void setSoluonghethong(int soluonghethong) { this.soluonghethong = soluonghethong; }
    public int getSoluongthucte() { return soluongthucte; }
    public void setSoluongthucte(int soluongthucte) { this.soluongthucte = soluongthucte; }
    public int getChenhlech() { return chenhlech; }
    public void setChenhlech(int chenhlech) { this.chenhlech = chenhlech; }
}
