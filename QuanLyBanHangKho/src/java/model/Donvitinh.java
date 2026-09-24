package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Donvitinh {
    private Long id;
    private Sanpham sanpham;
    private String madonvi;
    private String tendonvi;
    private int hesoquydoi = 1;
    private boolean donvicoso = false;
    public Donvitinh() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public String getMadonvi() { return madonvi; }
    public void setMadonvi(String madonvi) { this.madonvi = madonvi; }
    public String getTendonvi() { return tendonvi; }
    public void setTendonvi(String tendonvi) { this.tendonvi = tendonvi; }
    public int getHesoquydoi() { return hesoquydoi; }
    public void setHesoquydoi(int hesoquydoi) { this.hesoquydoi = hesoquydoi; }
    public boolean isDonvicoso() { return donvicoso; }
    public void setDonvicoso(boolean donvicoso) { this.donvicoso = donvicoso; }
}
