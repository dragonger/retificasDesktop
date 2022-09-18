package org.example.model;

import javax.persistence.Id;
import javax.persistence.Table;

@Table(name = "VENDEDOR")
public class VendedorModel {

    @Id
    Long id;
    String nome;
}
