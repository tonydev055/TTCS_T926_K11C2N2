package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietchuyenkho {
    private Long id;
    private Chuyenkho chuyenkho;
    private Sanpham sanpham;
    private int soluong;
    public Chitietchuyenkho() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Chuyenkho getChuyenkho() { return chuyenkho; }
    public void setChuyenkho(Chuyenkho chuyenkho) { this.chuyenkho = chuyenkho; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluong() { return soluong; }
    public void setSoluong(int soluong) { this.soluong = soluong; }
}
