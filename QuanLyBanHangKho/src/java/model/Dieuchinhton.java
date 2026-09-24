package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Dieuchinhton {
    private Long id;
    private Kho kho;
    private Sanpham sanpham;
    private int soluongtruoc;
    private int soluongsau;
    private String lydo;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String nguoithuchien;
    public Dieuchinhton() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluongtruoc() { return soluongtruoc; }
    public void setSoluongtruoc(int soluongtruoc) { this.soluongtruoc = soluongtruoc; }
    public int getSoluongsau() { return soluongsau; }
    public void setSoluongsau(int soluongsau) { this.soluongsau = soluongsau; }
    public String getLydo() { return lydo; }
    public void setLydo(String lydo) { this.lydo = lydo; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getNguoithuchien() { return nguoithuchien; }
    public void setNguoithuchien(String nguoithuchien) { this.nguoithuchien = nguoithuchien; }
}
