package com.example.orders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.catalog.CatalogClient;
import com.example.orders.catalog.OutOfStockException;
import com.example.orders.catalog.Product;
import com.example.orders.catalog.ProductNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogClient catalogClient;

    @Test
    void placesOrderAndReadsItBack() throws Exception {
        when(catalogClient.getProduct(7L))
                .thenReturn(new Product(7L, "Widget", "tools", new BigDecimal("12.50"), 10));

        String location = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c-1\",\"productId\":7,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Widget"));
    }

    @Test
    void returns404WhenProductDoesNotExist() throws Exception {
        when(catalogClient.getProduct(anyLong())).thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c-1\",\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns409WhenOutOfStock() throws Exception {
        when(catalogClient.getProduct(3L))
                .thenReturn(new Product(3L, "Gadget", "tools", BigDecimal.ONE, 0));
        doThrow(new OutOfStockException(3L, 5)).when(catalogClient).reserveStock(eq(3L), anyInt());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c-1\",\"productId\":3,\"quantity\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"\",\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesStats() throws Exception {
        mockMvc.perform(get("/api/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CREATED").exists());
    }
}
