package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Nguoidung {
    private Long id;
    private String email;
    private String matkhau;
    private String hoten;
    private String sodienthoai;
    private String anhdaidien;
    private String diaban;
    private boolean trangthai = true;
    private int solandangnhapsai = 0;
    private int phienbanphien = 0;
    private LocalDateTime khoatamden;
    private String lydokhoa;
    private LocalDateTime thoigiantao = LocalDateTime.now();
    private Set<Vaitro> vaitro = new HashSet<>();
    private Set<Kho> kho = new HashSet<>();
    public Nguoidung() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMatkhau() { return matkhau; }
    public void setMatkhau(String matkhau) { this.matkhau = matkhau; }
    public String getHoten() { return hoten; }
    public void setHoten(String hoten) { this.hoten = hoten; }
    public String getSodienthoai() { return sodienthoai; }
    public void setSodienthoai(String sodienthoai) { this.sodienthoai = sodienthoai; }
    public String getAnhdaidien() { return anhdaidien; }
    public void setAnhdaidien(String anhdaidien) { this.anhdaidien = anhdaidien; }
    public String getDiaban() { return diaban; }
    public void setDiaban(String diaban) { this.diaban = diaban; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
    public int getSolandangnhapsai() { return solandangnhapsai; }
    public void setSolandangnhapsai(int solandangnhapsai) { this.solandangnhapsai = solandangnhapsai; }
    public int getPhienbanphien() { return phienbanphien; }
    public void setPhienbanphien(int phienbanphien) { this.phienbanphien = phienbanphien; }
    public LocalDateTime getKhoatamden() { return khoatamden; }
    public void setKhoatamden(LocalDateTime khoatamden) { this.khoatamden = khoatamden; }
    public String getLydokhoa() { return lydokhoa; }
    public void setLydokhoa(String lydokhoa) { this.lydokhoa = lydokhoa; }
    public LocalDateTime getThoigiantao() { return thoigiantao; }
    public void setThoigiantao(LocalDateTime thoigiantao) { this.thoigiantao = thoigiantao; }
    public Set<Vaitro> getVaitro() { return vaitro; }
    public void setVaitro(Set<Vaitro> vaitro) { this.vaitro = vaitro; }
    public Set<Kho> getKho() { return kho; }
    public void setKho(Set<Kho> kho) { this.kho = kho; }
}
