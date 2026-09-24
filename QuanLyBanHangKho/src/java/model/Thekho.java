package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Thekho {
    private Long id;
    private Kho kho;
    private Sanpham sanpham;
    private String loaigiaodich;
    private String mathamchieu;
    private int soluongnhap = 0;
    private int soluongxuat = 0;
    private int toncuoi;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String diengiai;
    public Thekho() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public String getLoaigiaodich() { return loaigiaodich; }
    public void setLoaigiaodich(String loaigiaodich) { this.loaigiaodich = loaigiaodich; }
    public String getMathamchieu() { return mathamchieu; }
    public void setMathamchieu(String mathamchieu) { this.mathamchieu = mathamchieu; }
    public int getSoluongnhap() { return soluongnhap; }
    public void setSoluongnhap(int soluongnhap) { this.soluongnhap = soluongnhap; }
    public int getSoluongxuat() { return soluongxuat; }
    public void setSoluongxuat(int soluongxuat) { this.soluongxuat = soluongxuat; }
    public int getToncuoi() { return toncuoi; }
    public void setToncuoi(int toncuoi) { this.toncuoi = toncuoi; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getDiengiai() { return diengiai; }
    public void setDiengiai(String diengiai) { this.diengiai = diengiai; }
}
