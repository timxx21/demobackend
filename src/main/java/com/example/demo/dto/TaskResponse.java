//package com.example.demo.dto;
//
//import lombok.Data;
//import lombok.AllArgsConstructor;
//import java.util.UUID;
//
//@Data
//@AllArgsConstructor
//public class TaskResponse {
//    private UUID task_id;
//    private String status;
//    private int urls_count;
//
//}
//package com.example.demo.dto;
//
//import jakarta.persistence.Entity; // Добавлено
//import jakarta.persistence.Id;     // Добавлено
//import lombok.Data;
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;  // Добавлено
//import java.util.UUID;
//
//@Data
//@AllArgsConstructor
//@NoArgsConstructor // Добавлено: нужно для JPA
//@Entity            // Добавлено: связываем класс с БД
//public class TaskResponse {
//    @Id            // Добавлено: делаем первичным ключом
//    private UUID task_id;
//    private String status;
//    private int urls_count;
//}
package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Entity // Магия: превращаем DTO в таблицу бэка!
@Table(name = "check_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID task_id; // Наш UUID задачи теперь является PRIMARY KEY!

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int urls_count;
}
