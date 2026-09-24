package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Lohang {
    private Long id;
    private Kho kho;
    private Sanpham sanpham;
    private String malo;
    private LocalDate hansudung;
    private int soluongton = 0;
    private int soluonggiu = 0;
    private LocalDateTime thoigiannhap = LocalDateTime.now();
    public Lohang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public String getMalo() { return malo; }
    public void setMalo(String malo) { this.malo = malo; }
    public LocalDate getHansudung() { return hansudung; }
    public void setHansudung(LocalDate hansudung) { this.hansudung = hansudung; }
    public int getSoluongton() { return soluongton; }
    public void setSoluongton(int soluongton) { this.soluongton = soluongton; }
    public int getSoluonggiu() { return soluonggiu; }
    public void setSoluonggiu(int soluonggiu) { this.soluonggiu = soluonggiu; }
    public LocalDateTime getThoigiannhap() { return thoigiannhap; }
    public void setThoigiannhap(LocalDateTime thoigiannhap) { this.thoigiannhap = thoigiannhap; }
}
