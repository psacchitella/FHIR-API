package events

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/segmentio/kafka-go"
)

const (
	ClaimEventsTopic = "fhir.claim.events"
	EOBEventsTopic   = "fhir.eob.events"
)

type ClaimEvent struct {
	ClaimID   string `json:"claimId"`
	PatientID string `json:"patientId"`
	EventType string `json:"eventType"`
	Detail    string `json:"detail"`
	Timestamp string `json:"timestamp"`
}

var writer *kafka.Writer

func StartProducer(brokers []string) {
	writer = &kafka.Writer{
		Addr:         kafka.TCP(brokers...),
		Balancer:     &kafka.LeastBytes{},
		BatchTimeout: 10 * time.Millisecond,
	}
	log.Println("Kafka producer started")
}

func StopProducer() {
	if writer != nil {
		writer.Close()
	}
}

func PublishClaimEvent(claimID, patientID, eventType, detail string) {
	event := ClaimEvent{
		ClaimID:   claimID,
		PatientID: patientID,
		EventType: eventType,
		Detail:    detail,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	}

	topic := ClaimEventsTopic
	if eventType == "EOB_GENERATED" {
		topic = EOBEventsTopic
	}

	log.Printf("Publishing %s to %s for claim %s", eventType, topic, claimID)

	data, _ := json.Marshal(event)

	if writer != nil {
		err := writer.WriteMessages(context.Background(), kafka.Message{
			Topic: topic,
			Key:   []byte(claimID),
			Value: data,
		})
		if err != nil {
			log.Printf("Kafka write failed (event logged): %v", err)
		}
	} else {
		log.Printf("Event (no Kafka): %s", string(data))
	}
}
