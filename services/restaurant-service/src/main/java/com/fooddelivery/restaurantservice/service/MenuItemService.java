package com.fooddelivery.restaurantservice.service;

import com.fooddelivery.restaurantservice.domain.MenuItem;
import com.fooddelivery.restaurantservice.repository.MenuItemRepository;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.web.CreateMenuItemRequest;
import com.fooddelivery.restaurantservice.web.MenuItemResponse;
import com.fooddelivery.restaurantservice.web.UpdateMenuItemRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MenuItemService {

    private final MenuItemRepository   menuItemRepository;
    private final RestaurantRepository restaurantRepository;

    public MenuItemService(MenuItemRepository menuItemRepository,
                           RestaurantRepository restaurantRepository) {
        this.menuItemRepository   = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional
    public MenuItemResponse create(String restaurantId, CreateMenuItemRequest req) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new EntityNotFoundException("Restaurant not found: " + restaurantId);
        }
        var item = new MenuItem(UUID.randomUUID().toString(), restaurantId, req.name(), req.priceCents());
        return MenuItemResponse.from(menuItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> list(String restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId)
                .stream().map(MenuItemResponse::from).toList();
    }

    @Transactional
    public MenuItemResponse update(String restaurantId, String itemId, UpdateMenuItemRequest req) {
        MenuItem item = menuItemRepository.findById(itemId)
                .filter(i -> i.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new EntityNotFoundException("Menu item not found: " + itemId));
        item.update(req.name(), req.priceCents(), req.available());
        return MenuItemResponse.from(menuItemRepository.save(item));
    }

    @Transactional
    public void delete(String restaurantId, String itemId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .filter(i -> i.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new EntityNotFoundException("Menu item not found: " + itemId));
        menuItemRepository.delete(item);
    }
}
