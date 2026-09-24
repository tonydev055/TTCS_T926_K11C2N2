package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Nhacungcap {
    private Long id;
    private String manhacungcap;
    private String tennhacungcap;
    private String masothue;
    private String nguoilienhe;
    private String sodienthoai;
    private String email;
    private boolean trangthai = true;
    public Nhacungcap() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManhacungcap() { return manhacungcap; }
    public void setManhacungcap(String manhacungcap) { this.manhacungcap = manhacungcap; }
    public String getTennhacungcap() { return tennhacungcap; }
    public void setTennhacungcap(String tennhacungcap) { this.tennhacungcap = tennhacungcap; }
    public String getMasothue() { return masothue; }
    public void setMasothue(String masothue) { this.masothue = masothue; }
    public String getNguoilienhe() { return nguoilienhe; }
    public void setNguoilienhe(String nguoilienhe) { this.nguoilienhe = nguoilienhe; }
    public String getSodienthoai() { return sodienthoai; }
    public void setSodienthoai(String sodienthoai) { this.sodienthoai = sodienthoai; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
}
