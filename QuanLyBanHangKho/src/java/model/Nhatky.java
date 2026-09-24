package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Nhatky {
    private Long id;
    private String nguoidung;
    private String hanhdong;
    private String doituong;
    private String doituongid;
    private String giatricu;
    private String giatrimoi;
    private LocalDateTime thoigian = LocalDateTime.now();
    public Nhatky() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNguoidung() { return nguoidung; }
    public void setNguoidung(String nguoidung) { this.nguoidung = nguoidung; }
    public String getHanhdong() { return hanhdong; }
    public void setHanhdong(String hanhdong) { this.hanhdong = hanhdong; }
    public String getDoituong() { return doituong; }
    public void setDoituong(String doituong) { this.doituong = doituong; }
    public String getDoituongid() { return doituongid; }
    public void setDoituongid(String doituongid) { this.doituongid = doituongid; }
    public String getGiatricu() { return giatricu; }
    public void setGiatricu(String giatricu) { this.giatricu = giatricu; }
    public String getGiatrimoi() { return giatrimoi; }
    public void setGiatrimoi(String giatrimoi) { this.giatrimoi = giatrimoi; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
}
