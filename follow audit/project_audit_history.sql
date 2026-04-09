-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:57 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `project_audit_history`
--

CREATE TABLE `project_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `project_audit_history`
--

INSERT INTO `project_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(5, 144, 'Project', 'Details', 'Updated', 'Start Date', '2025-09-30', '2025-09-29', 'Alice Smith', '2025-11-12 16:43:08', '2025-11-12 16:43:08'),
(5, 145, 'Project', 'Details', 'Updated', 'End Date', '2025-10-22', '2025-10-21', 'Alice Smith', '2025-11-12 16:43:08', '2025-11-12 16:43:08'),
(9, 123, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'Alice Smith', '2025-11-02 16:48:27', '2025-11-02 16:48:27'),
(9, 124, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Project Manager', 'Alice Smith', '2025-11-02 16:48:27', '2025-11-02 16:48:27'),
(9, 125, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 16:48:27', '2025-11-02 16:48:27'),
(16, 7, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'dfsdf', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 8, 'Project', 'Details', 'Added', 'Description', NULL, 'sdfs', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 9, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ678', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 10, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 11, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 12, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 13, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 14, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 15, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-23 00:00:00.0', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 16, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 17, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(16, 18, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 11:11:59', '2025-10-23 11:11:59'),
(17, 19, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'hety', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 20, 'Project', 'Details', 'Added', 'Description', NULL, 'dfgh', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 21, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ760', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 22, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 23, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 24, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 25, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 26, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 27, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-23 00:00:00.0', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 28, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 29, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 30, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 31, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Project Owner', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 32, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(17, 33, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 11:22:05', '2025-10-23 11:22:05'),
(18, 34, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'trhdf', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 35, 'Project', 'Details', 'Added', 'Description', NULL, 'hdfghfg', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 36, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ893', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 37, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 38, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 39, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 40, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 41, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 42, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-23 00:00:00.0', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 43, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 44, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(18, 45, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:31:38', '2025-10-23 16:31:38'),
(19, 46, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'sdfvgdf', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 47, 'Project', 'Details', 'Added', 'Description', NULL, 'dfgdfg', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 48, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ103', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 49, 'Project', 'Details', 'Added', 'Parent Project', NULL, 'project 11', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 50, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 51, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 52, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 53, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 54, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 55, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-26 00:00:00.0', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 56, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 57, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(19, 58, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:51:06', '2025-10-26 15:51:06'),
(20, 59, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'fghfg', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 60, 'Project', 'Details', 'Added', 'Description', NULL, 'fghfg', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 61, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ119', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 62, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 63, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 64, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 65, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 66, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 67, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-26 00:00:00.0', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 68, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 69, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(20, 70, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 16:09:28', '2025-10-26 16:09:28'),
(21, 71, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'gdrgdfgf', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 72, 'Project', 'Details', 'Added', 'Description', NULL, 'dfgdfg', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 73, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ600', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 74, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 75, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 76, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 77, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 78, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 79, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-29 00:00:00.0', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 80, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 81, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(21, 82, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 10:19:45', '2025-10-29 10:19:45'),
(22, 83, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'ليبليب', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 84, 'Project', 'Details', 'Added', 'Description', NULL, 'erter', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 85, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ086', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 86, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 87, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 88, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-29 10:31:00', '2025-10-29 10:31:00'),
(22, 89, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(22, 90, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(22, 91, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-29 00:00:00.0', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(22, 92, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(22, 93, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(22, 94, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 10:31:01', '2025-10-29 10:31:01'),
(23, 95, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'fgdfgdfg', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 96, 'Project', 'Details', 'Added', 'Description', NULL, 'dfgdgdfgdf', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 97, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ856', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 98, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 99, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 100, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 101, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 102, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 103, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-29 00:00:00.0', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 104, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 105, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 106, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 11:00:59', '2025-10-29 11:00:59'),
(23, 107, 'Project', 'Details', 'Updated', 'Primary Name', 'fgdfgdfg', 'fgdfgdfg22', 'Alice Smith', '2025-10-29 11:01:54', '2025-10-29 11:01:54'),
(23, 108, 'Project', 'Details', 'Updated', 'Parent Project', NULL, NULL, 'Alice Smith', '2025-10-29 11:01:54', '2025-10-29 11:01:54'),
(23, 109, 'Project', 'Details', 'Updated', 'Classification', NULL, NULL, 'Alice Smith', '2025-10-29 11:01:54', '2025-10-29 11:01:54'),
(23, 110, 'Project', 'Details', 'Updated', 'Start Date', '2025-10-29 00:00:00.0', '2025-10-28 00:00:00.0', 'Alice Smith', '2025-10-29 11:01:54', '2025-10-29 11:01:54'),
(24, 111, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'TRSRA', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 112, 'Project', 'Details', 'Added', 'Description', NULL, 'FFCCC', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 113, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ737', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 114, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 115, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 116, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 117, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 118, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 119, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-10-29 00:00:00.0', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 120, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 121, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(24, 122, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-29 11:04:29', '2025-10-29 11:04:29'),
(25, 126, 'Project', 'Details', 'Added', 'Primary Name', NULL, 'project_test4', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 127, 'Project', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 128, 'Project', 'Details', 'Added', 'Reference Number', NULL, 'PRJ788', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 129, 'Project', 'Details', 'Added', 'Parent Project', NULL, 'project 7', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 130, 'Project', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 131, 'Project', 'Details', 'Status Change', 'Lifecycle Status', NULL, 'lif pr', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 132, 'Project', 'Details', 'Added', 'Project Type', NULL, 'm1', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 133, 'Project', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 134, 'Project', 'Details', 'Added', 'RAG', NULL, 'rag1', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 135, 'Project', 'Details', 'Added', 'Classification', NULL, 'classif 1', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 136, 'Project', 'Details', 'Added', 'Start Date', NULL, '2025-11-09', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 137, 'Project', 'Details', 'Added', 'End Date', NULL, '2025-10-26', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 138, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 139, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 140, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:36:24', '2025-11-09 13:36:24'),
(25, 141, 'Project', 'Details', 'Updated', 'Start Date', '2025-11-09', '2025-11-08', 'Alice Smith', '2025-11-09 13:37:22', '2025-11-09 13:37:22'),
(25, 142, 'Project', 'Details', 'Updated', 'End Date', '2025-10-26', '2025-10-25', 'Alice Smith', '2025-11-09 13:37:22', '2025-11-09 13:37:22'),
(25, 143, 'Stakeholder', 'edit', 'Updated', 'Role', 'Change Request OwnerR', 'Project Manager', 'System', '2025-11-09 13:37:23', '2025-11-09 13:37:23');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `project_audit_history`
--
ALTER TABLE `project_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `project_audit_history`
--
ALTER TABLE `project_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=146;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `project_audit_history`
--
ALTER TABLE `project_audit_history`
  ADD CONSTRAINT `Project_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `project` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
