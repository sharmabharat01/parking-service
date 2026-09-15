package com.liveparking.parkingservice.ingestion;

import com.liveparking.parkingservice.client.StaticCarParkClient;
import com.liveparking.parkingservice.dto.StaticCarParkDto;
import com.liveparking.parkingservice.repository.CarParkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaticCarParkIngestionService {

    private final StaticCarParkClient carParkClient;
    private final CarParkRepository carParkRepository;

    public StaticCarParkIngestionService(
            StaticCarParkClient carParkClient,
            CarParkRepository carParkRepository) {
        this.carParkClient = carParkClient;
        this.carParkRepository = carParkRepository;
    }

    public int ingest() {

        List<StaticCarParkDto> carParks =
                carParkClient.fetchCarParks();

        int saved = 0;

        for (StaticCarParkDto carPark : carParks) {

            if (!isValid(carPark)) {
                continue;
            }

            try {
                carParkRepository.save(carPark);
                saved++;
            } catch (RuntimeException e) {
                System.err.println(
                        "Failed to ingest car park: " + carPark.carParkNo()
                );
            }
        }

        return saved;
    }

    private boolean isValid(StaticCarParkDto carPark) {

        if (carPark == null) {
            return false;
        }

        if (isBlank(carPark.carParkNo())) {
            return false;
        }

        if (isBlank(carPark.address())) {
            return false;
        }

        if (!isValidCoordinate(carPark.xCoord())
                || !isValidCoordinate(carPark.yCoord())) {
            return false;
        }

        return true;
    }

    private boolean isValidCoordinate(String coordinate) {

        if (isBlank(coordinate)) {
            return false;
        }

        try {
            Double.parseDouble(coordinate);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}