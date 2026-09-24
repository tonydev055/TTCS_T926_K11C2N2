package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Matkhautoken {
    private Long id;
    private String token;
    private Nguoidung nguoidung;
    private LocalDateTime hethan;
    private boolean dadung = false;
    public Matkhautoken() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Nguoidung getNguoidung() { return nguoidung; }
    public void setNguoidung(Nguoidung nguoidung) { this.nguoidung = nguoidung; }
    public LocalDateTime getHethan() { return hethan; }
    public void setHethan(LocalDateTime hethan) { this.hethan = hethan; }
    public boolean isDadung() { return dadung; }
    public void setDadung(boolean dadung) { this.dadung = dadung; }
}
